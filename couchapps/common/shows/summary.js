function(doc, req) {

  var c = function(base, relative, text) {
    return "<li><a href=\"" + base + "/" + relative + "\">" + text + "</a></li>";
  };

  var f = function(base) {
    return "" +
      c(base, "bib_input/pointage.html", "Pointage") +
      c(base, "main_display/classement.html", "Voir le classement général") +
      c(base, "admin/admin.html", "Administration pointage") +
      c(base, "search/index.html", "Recherche");
  };

  return { body : "<!DOCTYPE html><html><head><meta http-equiv=\"content-type\" content=\"text/html; charset=utf-8\" />" +
    "<title>100 km Steenwerck</title>" +
    "<link rel=\"stylesheet\" href=\"style/main.css\" type=\"text/css\">" +
    "</head><body>" +
    "<h1>Bienvenue aux 100 kms de Steenwerck</h1>" +
    "<h2>Applications locales</h2><ul>" +
    "<li><a href=\"/_utils/database.html?steenwerck100km\">Accès direct à la base</a></li>" +
    f("../../..") +
    "</ul>" +
    "<h2>Applications sur le serveur</h2><ul>" +
    "<li><a href=\"http://steenwerck.rfc1149.net/_utils/\">Login serveur</a></li>" +
    "<li><a href=\"http://steenwerck.rfc1149.net/_utils/database.html?" + doc.dbname + "\">Accès direct à la base</a></li>" +
    f("http://steenwerck.rfc1149.net/" + doc.dbname + "/_design") +
    "</ul>" +
    "</body></html>"
  };
};
