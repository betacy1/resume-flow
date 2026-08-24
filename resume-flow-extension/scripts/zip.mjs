/**
 * 构建后打包 dist 目录为 zip 文件
 */
import AdmZip from 'adm-zip';
import { readdirSync, statSync } from 'fs';
import { join } from 'path';

const distDir = join(process.cwd(), 'dist');
const zipPath = join(process.cwd(), 'dist', 'resume-flow-extension.zip');

function addDirToZip(zip, dirPath, base = '') {
  const items = readdirSync(dirPath);
  for (const item of items) {
    const fullPath = join(dirPath, item);
    const entryName = base ? `${base}/${item}` : item;

    if (statSync(fullPath).isDirectory()) {
      addDirToZip(zip, fullPath, entryName);
    } else {
      zip.addLocalFile(fullPath, base);
    }
  }
}

const zip = new AdmZip();
addDirToZip(zip, distDir);
zip.writeZip(zipPath);
console.log(`[ResumeFlow] 插件打包完成: ${zipPath}`);
