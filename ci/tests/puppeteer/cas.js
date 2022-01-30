const assert = require('assert');
const axios = require('axios');
const https = require('https');
const {spawn} = require('child_process');
const waitOn = require('wait-on');
const jwt = require('jsonwebtoken');
const colors = require('colors');
const fs = require("fs");
const {ImgurClient} = require('imgur');
const path = require("path");
const { Buffer } = require('buffer');
const { PuppeteerScreenRecorder } = require('puppeteer-screen-recorder');
const ps = require("ps-node");
const NodeStaticAuth = require("node-static-auth");
const operativeSystemModule = require("os");

const BROWSER_OPTIONS = {
    ignoreHTTPSErrors: true,
    headless: process.env.CI === "true" || process.env.HEADLESS === "true",
    devtools: process.env.CI !== "true",
    defaultViewport: null,
    slowMo: process.env.CI === "true" ? 0 : 10,
    args: ['--start-maximized', "--window-size=1920,1080"]
};

exports.browserOptions = () => BROWSER_OPTIONS;
exports.browserOptions = (opt) => {
    return {
        ...BROWSER_OPTIONS,
        ...opt
    };
};

exports.logg = async(text) => {
    console.log(colors.green(text));
}

exports.removeDirectory = async (directory) => {
    await this.logg(`Removing directory ${directory}`);
    await fs.rmSync(directory, {recursive: true});
    await this.logg(`Removed directory ${directory}`);
    if (fs.existsSync(directory)) {
        await this.logg(`Directory still there... ${directory}`);
    }
}

exports.click = async (page, button) => {
    await page.evaluate((button) => {
        document.querySelector(button).click();
    }, button);
}

exports.clickLast = async (page, button) => {
    await page.evaluate((button) => {
        let buttons = document.querySelectorAll(button);
        buttons[buttons.length - 1].click();
    }, button);
}

exports.innerText = async (page, selector) => {
    let text = await page.$eval(selector, el => el.innerText.trim());
    console.log(`Text for selector [${selector}] is: [${text}]`);
    return text;
}

exports.textContent = async (page, selector) => {
    let element = await page.$(selector);
    let text = await page.evaluate(element => element.textContent.trim(), element);
    console.log(`Text content for selector [${selector}] is: [${text}]`);
    return text;
}

exports.inputValue = async (page, selector) => {
    const element = await page.$(selector);
    const text = await page.evaluate(element => element.value, element);
    console.log(`Input value for selector [${selector}] is: [${text}]`);
    return text;
}

exports.uploadImage = async (imagePath) => {
    let clientId = process.env.IMGUR_CLIENT_ID;
    if (clientId !== null && clientId !== undefined) {
        console.log(`Uploading image ${imagePath}`);
        const client = new ImgurClient({clientId: clientId});
        const response = await client.upload(imagePath);
        await this.logg(response.data.link);
    }
}

exports.loginWith = async (page, user, password,
                           usernameField = "#username",
                           passwordField = "#password") => {
    console.log(`Logging in with ${user} and ${password}`);
    await this.type(page, usernameField, user);
    await this.type(page, passwordField, password);
    await page.keyboard.press('Enter');
    await page.waitForNavigation();
}

exports.fetchGoogleAuthenticatorScratchCode = async (user = "casuser") => {
    console.log(`Fetching Scratch codes for ${user}...`);
    const response = await this.doRequest(`https://localhost:8443/cas/actuator/gauthCredentialRepository/${user}`,
        "GET", {
            'Accept': 'application/json'
        });
    return JSON.stringify(JSON.parse(response)[0].scratchCodes[0]);
}
exports.isVisible = async (page, selector) => {
    let element = await page.$(selector);
    console.log(`Checking visibility for ${selector} while on page ${page.url()}`);
    return (element != null && await element.boundingBox() != null);
}

exports.assertVisibility = async (page, selector) => {
    assert(await this.isVisible(page, selector));
}

exports.assertInvisibility = async (page, selector) => {
    let element = await page.$(selector);
    console.log(`Checking element invisibility for ${selector}`);
    assert(element == null || await element.boundingBox() == null);
}

exports.assertTicketGrantingCookie = async (page, present= true, cookieName = "TGC") => {
    const tgc = (await page.cookies()).filter(value => {
        console.log(`Checking cookie ${value.name}`)
        return value.name === cookieName
    });
    if (present) {
        assert(tgc.length !== 0);
        console.log(`Asserting ticket-granting cookie: ${tgc[0].value}`);
        return tgc[0];
    } else {
        assert(tgc.length === 0);
        console.log(`Ticket-granting cookie cannot be found`);
    }
}

exports.assertNoTicketGrantingCookie = async (page) => {
    let tgc = (await page.cookies()).filter(value => {
        console.log(`Checking cookie ${value.name}`)
        return value.name === "TGC"
    });
    console.log("Asserting no ticket-granting cookie: " + JSON.stringify(tgc) );
    assert(tgc.length === 0);
}

exports.submitForm = async (page, selector) => {
    console.log(`Submitting form ${selector}`);
    await page.$eval(selector, form => form.submit());
    await page.waitForTimeout(2500)
}

exports.type = async (page, selector, value, obfuscate = false) => {
    let logValue = obfuscate ? "******" : value;
    console.log(`Typing ${logValue} in field ${selector}`);
    await page.$eval(selector, el => el.value = '');
    await page.type(selector, value);
}

exports.newPage = async (browser) => {
    console.clear();
    let page = (await browser.pages())[0];
    if (page === undefined) {
        page = await browser.newPage();
    }
    await page.setDefaultNavigationTimeout(0);
    // await page.setRequestInterception(true);
    await page.bringToFront();
    return page;
}

exports.assertParameter = async (page, param) => {
    console.log(`Asserting parameter ${param} in URL: ${page.url()}`);
    let result = new URL(page.url());
    let value = result.searchParams.get(param);
    console.log(`Parameter ${param} with value ${value}`);
    assert(value != null);
    return value;
}

exports.assertMissingParameter = async (page, param) => {
    let result = new URL(page.url());
    assert(result.searchParams.has(param) === false);
}

exports.sleep = async (ms) => {
    return new Promise((resolve) => {
        this.logg(`Waiting for ${ms / 1000} second(s)...`)
        setTimeout(resolve, ms);
    });
}

exports.assertTicketParameter = async (page) => {
    console.log(`Page URL: ${page.url()}`);
    let result = new URL(page.url());
    assert(result.searchParams.has("ticket"))
    let ticket = result.searchParams.get("ticket");
    console.log(`Ticket: ${ticket}`);
    assert(ticket != null);
    return ticket;
}

exports.doRequest = async (url, method = "GET", headers = {}, statusCode = 200, requestBody = undefined) => {
    return new Promise((resolve, reject) => {
        let options = {
            method: method,
            rejectUnauthorized: false,
            headers: headers
        };
        console.log(`Contacting ${url} via ${method}`)
        const handler = (res) => {
            console.log(`Response status code: ${colors.green(res.statusCode)}`)
            if (statusCode > 0) {
                assert(res.statusCode === statusCode);
            }
            res.setEncoding("utf8");
            const body = [];
            res.on("data", chunk => body.push(chunk));
            res.on("end", () => resolve(body.join("")));
        };

        if (requestBody !== undefined) {
            let request = https.request(url, options, res => {
                handler(res);
            }).on("error", reject);
            request.write(requestBody);
        } else {
            https.get(url, options, res => {
                handler(res);
            }).on("error", reject);
        }
    });
}

exports.doGet = async (url, successHandler, failureHandler, headers = {}, responseType = undefined) => {
    const instance = axios.create({
        httpsAgent: new https.Agent({
            rejectUnauthorized: false
        })
    });
    let config = {
      headers: headers
    };
    if (responseType !== undefined) {
        config["responseType"] = responseType
    }
    await instance
        .get(url, config)
        .then(res => {
            if (responseType !== "blob" && responseType !== "stream") {
                console.log(res.data);
            }
            successHandler(res);
        })
        .catch(error => {
            failureHandler(error);
        })
}

exports.doPost = async (url, params, headers, successHandler, failureHandler) => {
    const instance = axios.create({
        httpsAgent: new https.Agent({
            rejectUnauthorized: false
        })
    });
    let urlParams = params instanceof URLSearchParams ? params : new URLSearchParams(params);
    await instance
        .post(url, urlParams, {headers: headers})
        .then(res => {
            console.log(res.data);
            successHandler(res);
        })
        .catch(error => {
            failureHandler(error);
        })
}

exports.waitFor = async (url, successHandler, failureHandler) => {
    let opts = {
        resources: [url],
        delay: 1000,
        interval: 2000,
        timeout: 120000
    };
    await waitOn(opts)
        .then(() => {
            successHandler("good")
        })
        .catch(err => {
            failureHandler(err);
        });
}

exports.getGradleCmd = async () => {
    let gradleCmd = './gradlew';
    if (operativeSystemModule.type() === 'Windows_NT') {
        gradleCmd = 'gradlew.bat';
    }
    return gradleCmd;
}

exports.launchWsFedSp = async (spDir, opts = []) => {
    let args = ['build', 'appStart', '-q', '-x', 'test', '--no-daemon', `-Dsp.sslKeystorePath=${process.env.CAS_KEYSTORE}`];
    args = args.concat(opts);
    await this.logg(`Launching WSFED SP in ${spDir} with ${args}`);
    let gradleCmd = await this.getGradleCmd()
    const exec = spawn(gradleCmd, args, {cwd: spDir});
    await this.logg(`Spawned process ID: ${exec.pid}`);
    exec.stdout.on('data', (data) => {
        console.log(data.toString());
    });
    exec.stderr.on('data', (data) => {
        console.error(data.toString());
    });
    exec.on('exit', (code) => {
        console.log(`Child process exited with code ${code}`);
    });
    return exec;
}

exports.stopGretty = async (gradleDir, deleteDir= true) => {
    let args = ['appStop', '-q', '--no-daemon'];
    await this.logg(`Stopping gretty in ${gradleDir} with ${args}`);
    let gradleCmd = await this.getGradleCmd()
    const exec = spawn(gradleCmd, args, {cwd: gradleDir});
    await this.logg(`Launched stop gretty process ID: ${exec.pid}`);
    exec.stdout.on('data', (data) => {
        console.log(data.toString());
    });
    exec.stderr.on('data', (data) => {
        console.error(data.toString());
    });
    exec.on('exit', (code) => {
        console.log(`Stopped child process exited with code ${code}`);
        if (deleteDir) {
            this.sleep(30000);
            this.removeDirectory(gradleDir);
        }
    });
    return exec;
}

exports.launchSamlSp = async (idpMetadataPath, samlSpDir, samlOpts = []) => {
    let keystorePath = path.normalize(process.env.CAS_KEYSTORE);
    let args = ['build', 'appStart', '-q', '-x', 'test', '--no-daemon',
        '-DidpMetadataType=idpMetadataFile',
        `-DidpMetadata=${idpMetadataPath}`,
        `-Dsp.sslKeystorePath=${keystorePath}`];
    args = args.concat(samlOpts);
    await this.logg(`Launching SAML2 SP in ${samlSpDir} with ${args}`);
    let gradleCmd = await this.getGradleCmd()
    const exec = spawn(gradleCmd, args, {cwd: samlSpDir});
    await this.logg(`Spawned process ID: ${exec.pid}`);
    exec.stdout.on('data', (data) => {
        console.log(data.toString());
    });
    exec.stderr.on('data', (data) => {
        console.error(data.toString());
    });
    exec.on('exit', (code) => {
        console.log(`Child process exited with code ${code}`);
    });
    return exec;
}

exports.stopActuator = async (baseUrl) => {
    let args = ['-k', '-X', 'POST', `${baseUrl}/actuator/shutdown`];
    await this.logg(`Stopping boot app via shutdown actuator with ${args}`);
    const exec = spawn("curl", args);
    exec.stdout.on('data', (data) => {
        console.log(data.toString());
    });
    exec.stderr.on('data', (data) => {
        console.error(data.toString());
    });
    exec.on('exit', (code) => {
        console.log(`Curl ran to invoke shutdown actuator and exited with code ${code}`);
    });
    return exec;
}

exports.assertInnerTextStartsWith = async (page, selector, value) => {
    const header = await this.innerText(page, selector);
    assert(header.startsWith(value));
}

exports.assertInnerTextContains = async (page, selector, value) => {
    const header = await this.innerText(page, selector);
    assert(header.includes(value));
}

exports.assertInnerText = async (page, selector, value) => {
    const header = await this.innerText(page, selector);
    assert(header === value)
}

exports.assertPageTitle = async (page, value) => {
    const title = await page.title();
    console.log(`Page Title: ${title}`);
    assert(title === value)
}

exports.recordScreen = async(page) => {
    let index = Math.floor(Math.random() * 10000);
    let filePath = path.join(__dirname, `/recording-${index}.mp4`)
    const config = {
        followNewTab: true,
        fps: 60,
        videoFrame: {
            width: 1024,
            height: 768,
        },
        aspectRatio: '4:3',
    };
    const recorder = new PuppeteerScreenRecorder(page, config);
    console.log(`Recording screen to ${filePath}`)
    await recorder.start(filePath);
    return recorder;
}

exports.decodeJwt = async (token, complete = false) => {
    console.log(`Decoding token ${token}`);
    let decoded = jwt.decode(token, {complete: complete});
    if (complete) {
        console.log(`Decoded token header: ${colors.green(decoded.header)}`);
        console.log("Decoded token payload:");
        await this.logg(decoded.payload);
    } else {
        console.log("Decoded token payload:");
        await this.logg(decoded);
    }
    return decoded;
}

exports.uploadSamlMetadata = async (page, metadata) => {
    await page.goto("https://samltest.id/upload.php");
    console.log(`Uploading metadata file ${metadata} to ${await page.url()}`);
    await page.waitForTimeout(1000)
    const fileElement = await page.$("input[type=file]");
    console.log(`Metadata file: ${metadata}`);
    await fileElement.uploadFile(metadata);
    await page.waitForTimeout(1000)
    await this.click(page, "input[name='submit']")
    await page.waitForNavigation();
    await page.waitForTimeout(2000)
}

exports.fetchDuoSecurityBypassCodes = async (user = "casuser") => {
    console.log(`Fetching Bypass codes from Duo Security for ${user}...`);
    const response = await this.doRequest(`https://localhost:8443/cas/actuator/duoAdmin/bypassCodes?username=${user}`,
        "POST", {
            'Accept': 'application/json',
            'Content-Type': 'application/json',
        });
    return JSON.parse(response)["mfa-duo"];
}

exports.fetchDuoSecurityBypassCode = async (user = "casuser") => {
    return await this.fetchDuoSecurityBypassCode(user)[0];
}

exports.base64Decode = async(data) => {
    let buff = Buffer.from(data, 'base64');
    return buff.toString('ascii');
}

exports.screenshot = async (page) => {
    let index = Math.floor(Math.random() * 10000);
    let filePath = path.join(__dirname, `/screenshot${index}.png`)
    try {
        await page.screenshot({path: filePath, fullPage: true});
        await this.logg(`Screenshot saved at ${filePath}`);
        await this.uploadImage(filePath);
    } catch (e)  {
        console.log(colors.red(`Unable to capture screenshot ${filePath}: ${e}`));
    }
}

exports.assertTextContent = async (page, selector, value) => {
    await page.waitForSelector(selector, {visible: true});
    let header = await this.textContent(page, selector);
    assert(header === value);
}

exports.assertTextContentStartsWith = async (page, selector, value) => {
    await page.waitForSelector(selector, {visible: true});
    let header = await this.textContent(page, selector);
    assert(header.startsWith(value));
}

exports.httpServer = async(root, port = 5432) => {
    const config = {
        nodeStatic: {
            root: root
        },
        server: {
            port: port,
            ssl: {
                enabled: false
            }
        },
        auth: {
            enabled: true,
            name: "restapi",
            pass: "YdCP05HvuhOH^*Z"
        },
        logger: {
            use: true,
            filename: 'restapi.log',
            folder: root
        }
    };
    new NodeStaticAuth(config);
}

exports.killProcess = async(command, arguments) => {
    ps.lookup({
        command: command,
        arguments: arguments
    }, (err, resultList) => {
        if (err) {
            throw new Error(err);
        }
        resultList.forEach(process => {
            console.log('PID: %s, COMMAND: %s, ARGUMENTS: %s',
                process.pid, process.command, process.arguments);
            if (process) {
                ps.kill(process.pid, err => {
                    if (err) {
                        throw new Error(err);
                    } else {
                        console.log('Process %s has been killed!', process.pid);
                    }
                });
            }
        });
    });
}

exports.loginDuoSecurityBypassCode = async (page, type) => {
    await page.waitForTimeout(12000);
    if (type === "websdk") {
        const frame = await page.waitForSelector("iframe#duo_iframe");
        await this.screenshot(page);
        const rect = await page.evaluate(el => {
            const {x, y, width, height} = el.getBoundingClientRect();
            return {x, y, width, height};
        }, frame);
        let x1 = rect.x + rect.width - 120;
        let y1 = rect.y + rect.height - 160;
        await page.mouse.click(x1, y1);
        await this.screenshot(page);
    } else {
        await this.click(page, "button#passcode");
    }
    let bypassCodes = await this.fetchDuoSecurityBypassCodes();
    console.log(`Duo Security ${type}: Retrieved bypass codes ${bypassCodes}`);
    if (type === "websdk") {
        let bypassCode = String(bypassCodes[0]);
        await page.keyboard.sendCharacter(bypassCode);
        await this.screenshot(page);
        console.log(`Submitting Duo Security bypass code ${bypassCode}`);
        await page.keyboard.down('Enter');
        await page.keyboard.up('Enter');
        await this.screenshot(page);
        console.log(`Waiting for Duo Security to accept bypass code for ${type}...`);
        await page.waitForTimeout(15000);
    } else {
        let i = 0;
        let error = false;
        while (!error && i < bypassCodes.length) {
            let bypassCode = `${String(bypassCodes[i])}`;
            await page.keyboard.sendCharacter(bypassCode);
            await this.screenshot(page);
            console.log(`Submitting Duo Security bypass code ${bypassCode}`);
            await this.type(page, "input[name='passcode']", bypassCode);
            await this.screenshot(page);
            await page.keyboard.press('Enter');
            console.log(`Waiting for Duo Security to accept bypass code...`);
            await page.waitForTimeout(10000);
            let error = await this.isVisible(page, "div.message.error");
            if (error) {
                console.log(`Duo Security is unable to accept bypass code`);
                await this.screenshot(page);
                i++;
            } else {
                console.log(`Duo Security accepted the bypass code ${bypassCode}`)
                return;
            }
        }
    }
}
