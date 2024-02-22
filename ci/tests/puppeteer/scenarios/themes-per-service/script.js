const puppeteer = require("puppeteer");
const assert = require("assert");
const cas = require("../../cas.js");

(async () => {
    const browser = await puppeteer.launch(cas.browserOptions());
    const page = await cas.newPage(browser);
    const service = "https://localhost:9859/anything/cas";
    await cas.gotoLogin(page, service);
    await cas.waitForResponse(page);
    await cas.assertVisibility(page, "#twitter-link");
    await cas.assertVisibility(page, "#youtube-link");

    const imgs = await page.$$eval("#cas-logo", (imgs) => imgs.map((img) => img.getAttribute("src")));
    const logo = imgs.pop();
    await cas.log(logo);
    assert(logo === "/cas/themes/example/images/logo.png");

    await cas.log("Logging out...");
    await cas.gotoLogout(page, service);
    await cas.waitForResponse(page);
    
    await cas.assertVisibility(page, "#twitter-link");
    await cas.assertVisibility(page, "#youtube-link");
    
    await cas.assertVisibility(page, "#logoutButton");
    await cas.submitForm(page, "#fm1");

    const url = await page.url();
    await cas.logPage(page);
    assert(url.toString().startsWith("https://localhost:8443/cas/logout"));
    await cas.assertCookie(page, false);

    await cas.assertVisibility(page, "#twitter-link");
    await cas.assertVisibility(page, "#youtube-link");
    
    await browser.close();
})();
