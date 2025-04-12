const outputFile = process.argv[1];
const url = process.argv[2];
const noSandbox = process.argv[3] === 'true';

console.log('outputFile: ' + outputFile);
console.log('url: ' + url);

const puppeteer = require('puppeteer');

function createArgs() {
    const args = [
        '--disable-gpu',
    ]
    if (noSandbox) {
        args.push('--no-sandbox');
    }
    return args;
}

(async () => {
    const args = createArgs();
    console.log('Running with args: ' + args);

    const browser = await puppeteer.launch({args: args});
    const page = await browser.newPage();

    console.log('Connecting to ' + url);
    await page.goto(url);

    console.log('Connected. Waiting for page content.');
    await page.content();

    console.log('Connected. Waiting for page to be ready.');
    await page.waitForFunction('isReady()');

    await page.pdf({
        path: outputFile,
        preferCSSPageSize: true,
        printBackground: true,
    });
    await browser.close();
})();