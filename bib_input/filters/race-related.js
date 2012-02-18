function(doc, req) {
  return !doc.type ||
         (doc.type != "site-info" &&
          doc.type != "touch-me" &&
	  doc.type != "ping");
}
