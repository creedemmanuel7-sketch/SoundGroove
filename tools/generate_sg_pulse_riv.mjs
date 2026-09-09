/**
 * Génère un .riv minimal (cercle pulse) pour SoundGroove.
 * Usage (depuis la racine du repo) :
 *   npm install @stevysmith/rive-generator --prefix tools
 *   node tools/generate_sg_pulse_riv.mjs
 */
import { writeFileSync, mkdirSync } from "fs";
import { dirname, join } from "path";
import { fileURLToPath, pathToFileURL } from "url";
import { createRequire } from "module";

const __dirname = dirname(fileURLToPath(import.meta.url));

async function loadGenerator() {
  try {
    return await import("@stevysmith/rive-generator");
  } catch {
    // Fallback : résolution locale (npm --prefix tools)
    const require = createRequire(import.meta.url);
    const pkgRoot = dirname(
      require.resolve("@stevysmith/rive-generator/package.json")
    );
    return import(pathToFileURL(join(pkgRoot, "dist", "index.js")).href);
  }
}

const { RiveFile, hex, PropertyKey } = await loadGenerator();

const outPath = join(
  __dirname,
  "..",
  "app",
  "src",
  "main",
  "res",
  "raw",
  "sg_pulse.riv"
);

const riv = new RiveFile();
const artboard = riv.addArtboard({ name: "Pulse", width: 120, height: 120 });
const shape = riv.addShape(artboard, { name: "Circle", x: 60, y: 60 });
riv.addEllipse(shape, { width: 36, height: 36 });
const fill = riv.addFill(shape);
riv.addSolidColor(fill, hex("#A78BFA"));

const anim = riv.addLinearAnimation(artboard, {
  name: "pulse",
  fps: 60,
  duration: 90,
  loop: "pingPong",
});

const keyed = riv.addKeyedObject(anim, shape);
for (const prop of [PropertyKey.scaleX, PropertyKey.scaleY]) {
  const key = riv.addKeyedProperty(keyed, prop);
  riv.addKeyFrameDouble(key, { frame: 0, value: 0.85, interpolation: "cubic" });
  riv.addKeyFrameDouble(key, { frame: 45, value: 1.15, interpolation: "cubic" });
  riv.addKeyFrameDouble(key, { frame: 90, value: 0.85, interpolation: "cubic" });
}

mkdirSync(dirname(outPath), { recursive: true });
writeFileSync(outPath, riv.export());
console.log("Wrote", outPath);
