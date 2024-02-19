const puppeteer = require("puppeteer");
const assert = require("assert");

const cas = require("../../cas.js");

(async () => {
    const browser = await puppeteer.launch(cas.browserOptions());
    const page = await cas.newPage(browser);

    await cas.gotoLogin(page);

    const pswd = await page.$("#password");
    assert(pswd === null);

    await cas.type(page,"#username", "casuser");
    await cas.pressEnter(page);
    await page.waitForNavigation();

    await cas.waitForTimeout(page, 1000);

    await cas.assertVisibility(page, "#token");
    await cas.waitForTimeout(page, 1000);

    const page2 = await browser.newPage();
    await page2.goto("http://localhost:8282");
    await cas.waitForTimeout(page2, 1000);
    await cas.click(page2, "table tbody td a");
    await cas.waitForTimeout(page2, 1000);
    const code =  await cas.textContent(page2, "div[name=bodyPlainText] .well");
    await page2.close();

    await cas.type(page, "#token", code);
    await cas.submitForm(page, "#fm1");
    await cas.waitForTimeout(page, 1000);

    await cas.assertInnerTextContains(page, "#attribute-tab-0 table#attributesTable tbody", "firstname");
    await cas.waitForTimeout(page, 2000);

    await browser.close();
})();