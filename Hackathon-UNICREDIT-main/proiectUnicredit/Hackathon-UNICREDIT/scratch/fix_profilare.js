const fs = require('fs');
const path = require('path');

const map = {
    ':': 'ți',
    '"': 'ș',
    '}': 'Î',
    'Ē': 'ă',
    ' ': 'ă', // Sometimes it's a space? No, let's see.
    'S': '✓',
    ' xa': '🎓',
    ' x': '💼',
    ' x': '🛠️',
    ' x ': '🔍',
    ' xR': '💵',
    ' x `': '💰',
    ' x:️': '📉',
    ' a ️': '📈',
    ' a': '⚡',
    ' x  ️': '🆘',
    '  ': '→',
    ' ': '€'
};

const file = 'Frontend/profilare.html';
const filePath = path.resolve('c:/Users/alinr/Desktop/proiectUnicredit/Hackathon-UNICREDIT', file);

if (fs.existsSync(filePath)) {
    let content = fs.readFileSync(filePath, 'utf8');
    
    // Specific fixes for words
    content = content.replace(/Situa:ie/g, 'Situație');
    content = content.replace(/}nvĒ:/g, 'Învăț');
    content = content.replace(/sus:in/g, 'susțin');
    content = content.replace(/întreagĒ/g, 'întreagă');
    content = content.replace(/obi"nuitĒ/g, 'obișnuită');
    content = content.replace(/PânĒ/g, 'Până');
    content = content.replace(/}ntre/g, 'Între');
    content = content.replace(/"tiu/g, 'știu');
    content = content.replace(/cumpĒr/g, 'cumpăr');
    content = content.replace(/prevĒzutĒ/g, 'prevăzută');
    content = content.replace(/Gânde"te-te/g, 'Gândește-te');
    content = content.replace(/a"a ceva/g, 'așa ceva');
    content = content.replace(/Rezumatul tĒu/g, 'Rezumatul tău');
    content = content.replace(/conversa:ia/g, 'conversația');
    content = content.replace(/IntrĒ/g, 'Intră');
    content = content.replace(/ContinuĒ/g, 'Continuă');
    content = content.replace(/}napoi/g, 'Înapoi');
    content = content.replace(/î:i/g, 'îți');
    content = content.replace(/pregĒte"te/g, 'pregătește');
    content = content.replace(/RĒspunde/g, 'Răspunde');
    content = content.replace(/întrebĒri/g, 'întrebări');
    content = content.replace(/personalizeze/g, 'personalizeze');
    content = content.replace(/recomandĒrile/g, 'recomandările');
    content = content.replace(/Profilul tĒu/g, 'Profilul tău');
    content = content.replace(/Construim profilul tĒu/g, 'Construim profilul tău');
    content = content.replace(/prima datĒ/g, 'prima dată');
    content = content.replace(/pregĒtit/g, 'pregătit');
    content = content.replace(/direc:ie/g, 'direcție');
    content = content.replace(/personalizatĒ/g, 'personalizată');
    content = content.replace(/în:eles/g, 'înțeles');
    content = content.replace(/potrivite/g, 'potrivite');
    content = content.replace(/recomandĒri/g, 'recomandări');
    content = content.replace(/Situa:ie/g, 'Situație');
    
    // Symbols
    content = content.replace(/  /g, '→');
    content = content.replace(/ /g, '€');
    content = content.replace(/  }/g, '← Î');
    
    // Bulk map for leftovers
    for (let key in map) {
        content = content.split(key).join(map[key]);
    }

    fs.writeFileSync(filePath, content, 'utf8');
    console.log('Fixed Frontend/profilare.html');
}
