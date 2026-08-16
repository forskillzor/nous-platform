/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

const fs = require('fs');
const path = require('path');

// Корень проекта (текущая директория)
const projectRoot = process.cwd();
const outputFile = path.join(projectRoot, 'project-structure.txt');

// Расширения файлов для включения
const includeExtensions = [
    '.kt', '.kts',                // Kotlin
    '.gradle', '.gradle.kts',      // Gradle
    '.toml',                       // Version catalogs
    'versions.toml',
    '.properties',                  // Свойства
    '.md', '.txt',                   // Документация
    '.xml',                           // Android ресурсы (если есть)
    '.json'                            // JSON конфиги
];

// Папки для ПОЛНОГО исключения (целиком)
const excludeFolders = [
    'build',
    '.idea',
    '.kotlin',
    'build-logic/build',
    'build-logic/.gradle',
    '.git',
    'out',
    'target',
    'bin',
    'gen'
];

// Файлы для исключения
const excludeFiles = [
    'gradle-wrapper.jar',
    'gradle-wrapper.properties',
    'local.properties',
    '*.lock',
    '*.iml',
    '.DS_Store',
    'thumbs.db'
];

// Папки, которые нужно ОБЯЗАТЕЛЬНО включить (даже если они в исключениях)
const forceInclude = [
    'gradle/libs.versions.toml',
    'gradle.properties',
    'settings.gradle.kts',
    'build.gradle.kts'
];

// Проверка, нужно ли исключить файл
function shouldExclude(filePath) {
    const relativePath = path.relative(projectRoot, filePath);
    const fileName = path.basename(filePath);

    // Проверяем force include
    if (forceInclude.some(pattern => {
        if (pattern.includes('*')) {
            const regex = new RegExp('^' + pattern.replace('*', '.*') + '$');
            return regex.test(fileName);
        }
        return relativePath === pattern || filePath.endsWith(pattern);
    })) {
        return false;
    }

    // Проверяем исключенные папки
    for (const folder of excludeFolders) {
        if (filePath.includes(path.sep + folder + path.sep) ||
            filePath.includes(folder + path.sep) ||
            filePath.endsWith(path.sep + folder)) {
            return true;
        }
    }

    // Проверяем исключенные файлы
    for (const pattern of excludeFiles) {
        if (pattern.includes('*')) {
            const regex = new RegExp('^' + pattern.replace('*', '.*') + '$');
            if (regex.test(fileName)) {
                return true;
            }
        } else if (fileName === pattern) {
            return true;
        }
    }

    return false;
}

// Рекурсивный сбор файлов
function collectFiles(dir, fileList = []) {
    if (!fs.existsSync(dir)) return fileList;

    try {
        const entries = fs.readdirSync(dir);

        for (const entry of entries) {
            const fullPath = path.join(dir, entry);

            if (shouldExclude(fullPath)) continue;

            const stat = fs.statSync(fullPath);

            if (stat.isDirectory()) {
                collectFiles(fullPath, fileList);
            } else {
                const ext = path.extname(entry).toLowerCase();
                if (includeExtensions.includes(ext)) {
                    fileList.push(fullPath);
                }
            }
        }
    } catch (error) {
        console.log(`⚠️ Ошибка доступа к ${dir}: ${error.message}`);
    }

    return fileList;
}

// Форматирование вывода
function formatFileContent(filePath) {
    const relativePath = path.relative(projectRoot, filePath).replace(/\\/g, '/');
    const content = fs.readFileSync(filePath, 'utf8');
    return `### FILE: ${relativePath}\n${content}\n\n`;
}

// Главная функция
function main() {
    console.log('🔍 Сканирование проекта...');
    console.log(`📁 Корень: ${projectRoot}`);

    const startTime = Date.now();

    // Собираем файлы
    const files = collectFiles(projectRoot);

    if (files.length === 0) {
        console.log('❌ Не найдено файлов для обработки!');
        return;
    }

    console.log(`📊 Найдено ${files.length} файлов`);

    // Сортируем для консистентности
    files.sort();

    // Группируем по типу для статистики
    const stats = {};
    files.forEach(f => {
        const ext = path.extname(f);
        stats[ext] = (stats[ext] || 0) + 1;
    });

    console.log('\n📊 Статистика по расширениям:');
    Object.entries(stats).forEach(([ext, count]) => {
        console.log(`   ${ext}: ${count}`);
    });

    // Записываем результат
    console.log('\n✍️ Запись в файл...');

    let output = '';
    let processed = 0;

    files.forEach(file => {
        try {
            output += formatFileContent(file);
            processed++;
            if (processed % 100 === 0) {
                console.log(`   Обработано ${processed}/${files.length}...`);
            }
        } catch (error) {
            console.log(`❌ Ошибка чтения ${file}: ${error.message}`);
        }
    });

    fs.writeFileSync(outputFile, output, 'utf8');

    const endTime = Date.now();
    const duration = ((endTime - startTime) / 1000).toFixed(2);

    console.log(`\n✅ Готово!`);
    console.log(`📁 Файлов сохранено: ${processed}`);
    console.log(`📦 Размер: ${(output.length / 1024).toFixed(2)} KB`);
    console.log(`⏱️ Время: ${duration} сек`);
    console.log(`📄 Результат: ${outputFile}`);

    // Покажем примеры найденных файлов
    console.log('\n📋 Примеры файлов:');
    files.slice(0, 10).forEach(f => {
        console.log(`   ${path.relative(projectRoot, f)}`);
    });
}

main();