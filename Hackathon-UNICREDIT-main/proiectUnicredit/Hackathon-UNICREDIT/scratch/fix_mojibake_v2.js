const fs = require('fs');
const path = require('path');

const map = {
    '\u00C8\u2122': 'ș',
    '\u00C8\u203A': 'ț',
    '\u00C4\u0192': 'ă',
    '\u00C3\u00AE': 'î',
    '\u00C3\u00A2': 'â',
    '\u00C3\u017D': 'Î',
    '\u00C3\u201A': 'Â',
    '\u00C2\u00B7': '·',
    '\u00E2\u20AC\u00A2': '•',
    '\u00E2\u20AC\u2013': '–',
    '\u00E2\u20AC\u2014': '—',
    '\u00E2\u20AC\u2122': '’',
    '\u00E2\u20AC\u0153': '“',
    '\u00E2\u20AC\u009D': '”',
    '\u00E2\u20AC\u017E': '„',
    '\u00E2\u20AC\u201C': '“',
    '\u00E2\u20AC\u009D': '”',
    '\u00E2\u20AC\u00AC': '€',
    '\u00E2\u2122\u00A6': '✦',
    '\u00E2\u2192': '→',
    '\u00E2\u2197': '↗',
    '\u00E2\u2714': '✔',
    '\u00E2\u2713': '✓',
    '\u00E2\u0153\u00A6': '✦',
    '\u00E2\u2020\u2019': '→',
    '\u00C3\u00A2': 'â',
    '\u00C3\u0102': 'Â',
    '\u00C8\u0219': 'ș',
    '\u00C8\u021B': 'ț',
    'Ã®': 'î',
    'Äƒ': 'ă',
    'È™': 'ș',
    'È›': 'ț',
    'Ã¢': 'â',
    'ÃŽ': 'Î',
    'Ã‚': 'Â',
    'Â·': '·',
    'â‚¬': '€',
    'â†’': '→',
    'â†—': '↗',
    'âœ¦': '✦',
    'âœ”': '✔',
    'âœ“': '✓',
    'Ã—': '×',
    'â€“': '–',
    'â€ž': '„',
    'â€œ': '“',
    'â€': '”',
    'â€”': '—'
};

const files = [
    'back/src/main/resources/static/index.html',
    'back/src/main/resources/static/profilare.html',
    'back/src/main/resources/static/chat.html',
    'Frontend/index.html',
    'Frontend/profilare.html',
    'Frontend/chat.html'
];

files.forEach(file => {
    const filePath = path.resolve('c:/Users/alinr/Desktop/proiectUnicredit/Hackathon-UNICREDIT', file);
    if (fs.existsSync(filePath)) {
        let content = fs.readFileSync(filePath, 'utf8');
        let original = content;
        for (let key in map) {
            content = content.split(key).join(map[key]);
        }
        if (content !== original) {
            fs.writeFileSync(filePath, content, 'utf8');
            console.log(`Fixed ${file}`);
        } else {
            console.log(`No change for ${file}`);
        }
    }
});
