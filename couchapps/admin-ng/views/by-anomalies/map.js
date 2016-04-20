function (doc) {
  if (doc.type == "analysis") {
    emit([doc.valid, -doc.anomalies], {"race_id": doc.race_id, "anomalies": doc.anomalies, "valid": doc.valid, "bib": doc.bib});
  }
}
