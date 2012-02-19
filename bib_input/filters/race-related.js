function(doc, req) {
  return !doc.type ||
         (doc.type != "touch-me" &&
          doc.type != "ping");
}
