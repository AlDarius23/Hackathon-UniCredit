const fs = require('fs');
const path = require('path');

const files = [
  'back/src/main/resources/static/index.html',
  'back/src/main/resources/static/profilare.html',
  'back/src/main/resources/static/chat.html',
  'Frontend/index.html',
  'Frontend/profilare.html',
  'Frontend/chat.html'
];

files.forEach(file => {
  const absolutePath = path.resolve('c:/Users/alinr/Desktop/proiectUnicredit/Hackathon-UNICREDIT', file);
  if (fs.existsSync(absolutePath)) {
    const content = fs.readFileSync(absolutePath, 'utf8');
    
    // Check if it has the specific corrupted sequences
    if (content.includes('Ã®') || content.includes('È™') || content.includes('Äƒ') || content.includes('ÃŽ') || 
        content.includes('Â·') || content.includes('È›') || content.includes('Ã¢') || content.includes('Ã‚') || 
        content.includes('â‚¬') || content.includes('â†’') || content.includes('âœ¦') || content.includes('â€ž') || 
        content.includes('Â°') || content.includes('Ã—') || content.includes('â€“')) {
      
      console.log(`Fixing mojibake in: ${file}`);
      const fixed = Buffer.from(content, 'latin1').toString('utf8');
      fs.writeFileSync(absolutePath, fixed, 'utf8');
    } else {
      console.log(`No mojibake detected in: ${file}`);
    }
  } else {
    console.log(`File not found: ${file}`);
  }
});
