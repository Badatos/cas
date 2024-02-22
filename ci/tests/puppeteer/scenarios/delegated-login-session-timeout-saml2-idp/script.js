const puppeteer = require("puppeteer");
const cas = require("../../cas.js");
const path = require("path");
const assert = require("assert");

(async () => {
    const browser = await puppeteer.launch(cas.browserOptions());
    const page = await cas.newPage(browser);

    await cas.gotoLogin(page);

    await cas.assertVisibility(page, "#loginProviders");
    await cas.assertVisibility(page, "li #SAML2Client");
    
    await cas.click(page, "li #SAML2Client");
    await cas.waitForNavigation(page);
    await cas.loginWith(page, "user1", "password");
    await cas.waitForTimeout(2000);
    await cas.screenshot(page);
    await cas.assertCookie(page, false);
    await cas.logPage(page);
    await cas.assertParameter(page, "client_name");
    const url = await page.url();
    assert(url.includes("https://localhost:8443/cas/login"));
    await cas.assertInnerText(page, "#content h2", "Application Not Authorized to Use CAS");
    await cas.removeDirectoryOrFile(path.join(__dirname, "/saml-md"));
    await browser.close();
})();

