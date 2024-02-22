const puppeteer = require("puppeteer");
const path = require("path");
const cas = require("../../cas.js");
const assert = require("assert");

async function cleanUp() {
    await cas.removeDirectoryOrFile(path.join(__dirname, "/saml-md"));
    await cas.log("Cleanup done");
}

(async () => {
    const browser = await puppeteer.launch(cas.browserOptions());
    const page = await cas.newPage(browser);
    const response = await cas.goto(page, "https://localhost:8443/cas/idp/metadata");
    await cas.log(`${response.status()} ${response.statusText()}`);
    assert(response.ok());

    await cas.waitFor("https://localhost:9876/sp/saml/status", async () => {
        try {
            await cas.goto(page, "https://localhost:9876/sp");
            await cas.waitForElement(page, "#idpForm");
            await cas.submitForm(page, "#idpForm");
            await cas.waitForElement(page, "#username");

            await cas.loginWith(page, "duobypass", "Mellon");

            await cas.log("Checking for page URL...");
            await cas.logPage(page);
            await cas.waitForElement(page, "#principal");
            await cas.assertInnerText(page, "#principal", "casuser@example.org");
            await cas.assertInnerText(page, "#authnContextClass", "https://refeds.org/profile/mfa");
        } finally {
            await browser.close();
            await cleanUp();
        }
    }, async (error) => {
        await cleanUp();
        await cas.log(error);
        throw error;
    });
})();

