import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const generationDir = new URL("../src/main/generation/", import.meta.url);
const workingDirectory = fileURLToPath(generationDir);
const loader = new URL("node_modules/ts-node/esm.mjs", generationDir);
const typescript = new URL("node_modules/typescript/lib/typescript.js", generationDir);
const dependencyStamp = new URL("node_modules/.projectexa-generator-deps", generationDir);
const dependencyHash = createHash("sha256")
    .update(readFileSync(new URL("package.json", generationDir)))
    .update(readFileSync(new URL("package-lock.json", generationDir)))
    .digest("hex");

function run(command, args, shell = false) {
    const result = spawnSync(command, args, {
        cwd: workingDirectory,
        stdio: "inherit",
        windowsHide: true,
        shell
    });
    if (result.error) {
        console.error(`Cannot start ${command}: ${result.error.message}. Install Node.js and npm and add them to PATH.`);
        process.exit(1);
    }
    if (result.status !== 0) process.exit(result.status ?? 1);
}

const installedHash = existsSync(dependencyStamp) ? readFileSync(dependencyStamp, "utf8") : "";
if (installedHash !== dependencyHash || !existsSync(loader) || !existsSync(typescript)) {
    // Only fixed arguments are passed through cmd.exe; paths go through cwd.
    const windows = process.platform === "win32";
    run(windows ? "npm.cmd" : "npm", ["ci", "--ignore-scripts", "--no-audit", "--no-fund"], windows);
    writeFileSync(dependencyStamp, dependencyHash);
}

run(process.execPath, [
    "--no-warnings",
    "--no-deprecation",
    "--loader", loader.href,
    fileURLToPath(new URL("run.ts", generationDir))
]);
