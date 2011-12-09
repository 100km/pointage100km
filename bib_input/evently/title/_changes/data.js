function(data) {
    // Set title for the document
    document.title = "Pointage " + data.name;

    return {
        site_name : data.name,
        site_id : data.site_id
    }
};
