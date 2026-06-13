import fs from "node:fs";

const root = process.cwd();
const clientMap = `${root}/build/tools/mojang-client-1.21.11.txt`;
const serverMap = `${root}/build/tools/mojang-server-1.21.11.txt`;
const intermediaryTiny = `${root}/build/tools/intermediary-mappings.tiny`;
const outTiny = `${root}/build/tools/mojang-named-to-intermediary.tiny`;

function parseMojang(files) {
  const classes = new Map();
  const namedToObf = new Map();

  for (const file of files) {
    const lines = fs.readFileSync(file, "utf8").split(/\r?\n/);
    let current = null;

    for (const line of lines) {
      if (!line || line.startsWith("#")) continue;

      const classMatch = line.match(/^(\S+) -> (\S+):$/);
      if (classMatch) {
        const named = classMatch[1].replaceAll(".", "/");
        const obf = classMatch[2].replaceAll(".", "/");
        current = classes.get(named);

        if (!current) {
          current = { named, obf, fields: [], methods: [] };
          classes.set(named, current);
        }

        namedToObf.set(named, obf);
        continue;
      }

      if (!current || !line.startsWith("    ") || line.trimStart().startsWith("#")) continue;

      let source = line.trim();
      while (/^\d+:\d+:/.test(source)) {
        source = source.replace(/^\d+:\d+:/, "");
      }

      const arrow = source.lastIndexOf(" -> ");
      if (arrow < 0) continue;

      const left = source.slice(0, arrow).trim();
      const obfName = source.slice(arrow + 4).trim();

      if (left.includes("(")) {
        const methodMatch = left.match(/^(.*?)\s+([^\s]+)\((.*)\)$/);
        if (!methodMatch) continue;

        current.methods.push({
          returnType: methodMatch[1],
          name: methodMatch[2],
          params: methodMatch[3]
            ? methodMatch[3].split(",").map((value) => value.trim()).filter(Boolean)
            : [],
          obfName,
        });
      } else {
        const fieldMatch = left.match(/^(.*?)\s+([^\s]+)$/);
        if (!fieldMatch) continue;

        current.fields.push({
          type: fieldMatch[1],
          name: fieldMatch[2],
          obfName,
        });
      }
    }
  }

  return { classes, namedToObf };
}

function parseIntermediary(file) {
  const classes = new Map();
  const fields = new Map();
  const methods = new Map();
  const lines = fs.readFileSync(file, "utf8").split(/\r?\n/);
  let owner = null;

  for (const line of lines) {
    if (line.startsWith("c\t")) {
      const [, obf, intermediary] = line.split("\t");
      owner = obf;
      classes.set(obf, intermediary);
    } else if (owner && line.startsWith("\tf\t")) {
      const [, , descriptor, obfName, intermediaryName] = line.split("\t");
      fields.set(`${owner}\t${obfName}\t${descriptor}`, intermediaryName);
    } else if (owner && line.startsWith("\tm\t")) {
      const [, , descriptor, obfName, intermediaryName] = line.split("\t");
      methods.set(`${owner}\t${obfName}\t${descriptor}`, intermediaryName);
    }
  }

  return { classes, fields, methods };
}

const primitiveDescriptors = new Map([
  ["void", "V"],
  ["boolean", "Z"],
  ["byte", "B"],
  ["char", "C"],
  ["short", "S"],
  ["int", "I"],
  ["long", "J"],
  ["float", "F"],
  ["double", "D"],
]);

function typeDescriptor(type, classMapper) {
  let dimensions = 0;
  while (type.endsWith("[]")) {
    dimensions += 1;
    type = type.slice(0, -2);
  }

  const base = primitiveDescriptors.get(type) ?? `L${classMapper(type.replaceAll(".", "/"))};`;
  return `${"[".repeat(dimensions)}${base}`;
}

function methodDescriptor(params, returnType, classMapper) {
  return `(${params.map((param) => typeDescriptor(param, classMapper)).join("")})${typeDescriptor(returnType, classMapper)}`;
}

const mojang = parseMojang([clientMap, serverMap]);
const intermediary = parseIntermediary(intermediaryTiny);
const namedToObf = (internalName) => mojang.namedToObf.get(internalName) ?? internalName;

let classCount = 0;
let fieldCount = 0;
let methodCount = 0;
const output = ["tiny\t2\t0\tmojang\tintermediary"];
const seenClasses = new Set();

for (const classMapping of mojang.classes.values()) {
  const intermediaryClass = intermediary.classes.get(classMapping.obf);
  if (!intermediaryClass || seenClasses.has(classMapping.named)) continue;

  seenClasses.add(classMapping.named);
  output.push(`c\t${classMapping.named}\t${intermediaryClass}`);
  classCount += 1;

  const seenMembers = new Set();

  for (const field of classMapping.fields) {
    const namedDescriptor = typeDescriptor(field.type, (value) => value);
    const obfDescriptor = typeDescriptor(field.type, namedToObf);
    const intermediaryField = intermediary.fields.get(`${classMapping.obf}\t${field.obfName}\t${obfDescriptor}`);
    if (!intermediaryField) continue;

    const key = `f\t${namedDescriptor}\t${field.name}`;
    if (seenMembers.has(key)) continue;

    seenMembers.add(key);
    output.push(`\tf\t${namedDescriptor}\t${field.name}\t${intermediaryField}`);
    fieldCount += 1;
  }

  for (const method of classMapping.methods) {
    if (method.name === "<init>" || method.name === "<clinit>") continue;

    const namedDescriptor = methodDescriptor(method.params, method.returnType, (value) => value);
    const obfDescriptor = methodDescriptor(method.params, method.returnType, namedToObf);
    const intermediaryMethod = intermediary.methods.get(`${classMapping.obf}\t${method.obfName}\t${obfDescriptor}`);
    if (!intermediaryMethod) continue;

    const key = `m\t${namedDescriptor}\t${method.name}`;
    if (seenMembers.has(key)) continue;

    seenMembers.add(key);
    output.push(`\tm\t${namedDescriptor}\t${method.name}\t${intermediaryMethod}`);
    methodCount += 1;
  }
}

fs.writeFileSync(outTiny, `${output.join("\n")}\n`, "utf8");
console.log(JSON.stringify({ outTiny, classCount, fieldCount, methodCount }, null, 2));
