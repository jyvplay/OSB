#!/usr/bin/env node
// SPDX-License-Identifier: Apache-2.0
// R87 independent spreadsheet reader lane: open every .xlsx in a directory with exceljs (a third-party
// workbook library that is neither our writer nor openpyxl) and dump the view state it reports.
//
// exceljs does not model `showSheetTabs`, so that one attribute stays with the openpyxl/openpyxl-based
// R86 lane and is reported here as `null` rather than guessed.
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const ExcelJS = require('exceljs');
const pins = JSON.parse(fs.readFileSync(new URL('./package.json', import.meta.url), 'utf8')).dependencies;

const [directory, output] = process.argv.slice(2);
if (!directory || !output) {
  console.error('usage: read_xlsx.mjs <artifact-dir> <output.json>');
  process.exit(2);
}

const files = [];
const errors = [];
for (const name of fs.readdirSync(directory).filter((entry) => entry.endsWith('.xlsx')).sort()) {
  const workbook = new ExcelJS.Workbook();
  try {
    await workbook.xlsx.readFile(path.join(directory, name));
  } catch (error) {
    errors.push({ name, error: String((error && error.message) || error) });
    continue;
  }
  const view = (workbook.views && workbook.views[0]) || {};
  const sheets = [];
  // R89: the font of each populated cell, as exceljs models it (size in points, colour as ARGB).
  const cellFonts = (sheet) => {
    const cells = [];
    sheet.eachRow({ includeEmpty: false }, (row) => {
      row.eachCell({ includeEmpty: false }, (cell) => {
        const font = cell.font || {};
        cells.push({
          ref: cell.address,
          value: cell.value === null || cell.value === undefined ? null : String(cell.value),
          size: font.size === undefined ? null : font.size,
          bold: font.bold === true,
          italic: font.italic === true,
          color: font.color && font.color.argb ? font.color.argb : null,
        });
      });
    });
    return cells;
  };
  workbook.eachSheet((sheet) => {
    const sheetView = (sheet.views && sheet.views[0]) || {};
    const colour = sheet.properties && sheet.properties.tabColor;
    sheets.push({
      name: sheet.name,
      state: sheet.state || 'visible',
      tabArgb: colour ? colour.argb || null : null,
      zoomScale: sheetView.zoomScale === undefined ? null : sheetView.zoomScale,
      showGridLines: sheetView.showGridLines === undefined ? null : sheetView.showGridLines,
      cells: cellFonts(sheet),
    });
  });
  files.push({
    name,
    firstSheet: view.firstSheet === undefined ? null : view.firstSheet,
    activeTab: view.activeTab === undefined ? null : view.activeTab,
    showSheetTabs: null,
    sheets,
  });
}

const payload = { reader: `exceljs@${pins.exceljs}`, directory, files, errors };
fs.writeFileSync(output, `${JSON.stringify(payload, null, 1)}\n`);
for (const error of errors) console.error(`read failed: ${error.name}: ${error.error}`);
console.log(`R87 exceljs: ${payload.reader} read ${files.length} workbook(s), ${errors.length} failure(s)`);
process.exit(errors.length === 0 ? 0 : 4);
