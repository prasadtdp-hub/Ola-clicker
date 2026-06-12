const fs = require('fs');

const codes = new Set();
while(codes.size < 1000) {
    const code = Math.floor(1000000 + Math.random() * 9000000).toString();
    codes.add(code);
}
const codeArray = Array.from(codes);

const content = `package com.example

object ActivationCodes {
    val validCodes = setOf(
        ${codeArray.map(c => `"${c}"`).join(',\n        ')}
    )
}
`;

fs.writeFileSync('app/src/main/java/com/example/ActivationCodes.kt', content);

fs.writeFileSync('activation_codes.txt', codeArray.join('\n'));
console.log('Generated app/src/main/java/com/example/ActivationCodes.kt and activation_codes.txt');
