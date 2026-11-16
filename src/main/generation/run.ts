import { exists } from "./util";
import { mkdir, readdir, rm } from "node:fs/promises";
import { isAbsolute, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";

const inDir = new URL("generators/", import.meta.url);
const resourcesRoot = resolve(fileURLToPath(new URL("../../generated/resources/", import.meta.url)));

interface ModuleIndex {
    format: string;
    outDir: URL;
}

interface ModuleRunner {
    default(this: void, outDir: URL): Promise<Array<[string, string]>>;
}

async function run(dir: URL) {
    const { format, outDir: out } = await import(new URL("index.ts", dir).href) as ModuleIndex;
    const target = resolve(fileURLToPath(out));
    const fromRoot = relative(resourcesRoot, target);
    if (!fromRoot || fromRoot === ".." || fromRoot.startsWith(`..${sep}`) || isAbsolute(fromRoot)) {
        throw new Error(`Generator output must be inside ${resourcesRoot}: ${target}`);
    }
    if (await exists(out)) {
        await rm(target, { recursive: true, force: true });
    }
    await mkdir(out, { recursive: true });
    for (const gen of (await readdir(dir, { withFileTypes: true }))) {
        if (gen.isDirectory() || !gen.name.endsWith(".ts") || gen.name === "index.ts") {
            continue;
        }
        const { default: mod } = await import(new URL(gen.name, dir).href) as ModuleRunner;
        // @ts-expect-error arrays can be empty, ts begs to differ
        const res = (await mod(out)).filter(arr => arr.length !== 0);
        for (const [input, output] of res) {
            if (dir.pathname.endsWith("lang/")) {
                console.log(format.trim(), gen.name, output.split("/").slice(-1)[0].replace(/\.json/, ""));
            } else {
                console.log(format.trim(), input.replace(dir.pathname, "").replace(/\//, ""), gen.name, output.replace(out.pathname, "").replace(/\.json/, ""));
            }
        }
    }
}

const mod = await readdir(inDir, { withFileTypes: true });
for (const dir of mod) {
    if (dir.isDirectory()) await run(new URL(`${dir.name}/`, inDir));
}
