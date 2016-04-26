function(doc) {
  if (doc.type === "sms")
    emit(-doc.timestamp, null);
}
