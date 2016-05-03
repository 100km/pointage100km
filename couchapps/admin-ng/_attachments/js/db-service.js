angular.module("steenwerck.database", []).factory("dbService",
    ["$http", "database", function($http, database) {

      var uuid = function() {
        return $http.get(database + "/../../_uuids").then(response => response.data.uuids[0]);
      };

      var updateStalker = bib =>
        database + "/_design/admin/_update/change-stalker/contestant-" + bib;

      return {

        checkSites: function() {
          return $http
            .get(database + "/_design/admin/_view/alive?group_level=1&update_seq=true")
            .then(response => response.data);
        },

        fixCheckpoint: function(contestantId, raceId, siteId, timestamp, action) {
          var docid = "checkpoints-" + siteId + "-" + contestantId;
          var payload = { bib: contestantId, race_id: raceId, site_id: siteId,
            timestamp: timestamp, action: action };
          return $http.put(database + "/_design/admin-ng/_update/fix-checkpoint/" +
              docid, payload);
        },

        getAlertsFrom: function(offset, limit) {
          return $http.get(database + "/_design/admin/_view/alerts?skip=" + offset +
             "&limit=" + limit);
        },

        getSMSFor: function(bib) {
          return $http.get(database + "/_design/admin-ng/_view/sms-by-bib",
              { params: { startkey: JSON.stringify([bib]), endkey: JSON.stringify([bib+1]),
                          inclusive_end: false, include_docs: true } });
        },

        getSMSFrom: function(offset, limit) {
            return $http.get(database + "/_design/admin-ng/_view/sms-all?skip=" +
                offset + "&limit=" + limit + "&include_docs=true");
        },

        getCheckpointsFrom: function(siteId, offset, limit) {
          return $http.get(database + "/_design/admin-ng/_view/at-checkpoint",
              { params: { startkey: JSON.stringify([siteId]),
                          endkey: JSON.stringify([siteId+1]),
                          inclusive_end: false,
                          skip: offset, limit: limit } });
        },

        getAnalysesFrom: function(offset, limit) {
          return $http.get(database + "/_design/admin-ng/_view/by-anomalies?skip=" +
              offset + "&limit=" + limit);
        },

        getMessages: function() {
          return $http.get(database + "/_design/admin/_view/all-valid-messages",
              { params: { startkey: JSON.stringify([true]) } });
        },

        deleteMessage: function(id) {
          return $http.put(database + "/_design/admin-ng/_update/delete-message/" + id, {});
        },

        uuid: uuid,

        createMessage: function(message, target) {
          uuid().then(uuid => {
            var id = "message-" + uuid;
            var msg = {message: message, addedTS: Date.now(), type: "message"};
            if (target !== undefined)
              msg.target = target;
            return $http.put(database + "/" + id, msg);
          });
        },

        getStalkers: function(bib) {
          return $http.get(database + "/contestant-" + bib)
            .then(response => response.data.stalkers || []);
        },

        addStalker: function(bib, stalker) {
          return $http.put(updateStalker(bib), {operation: "add", stalker: stalker});
        },

        removeStalker: function(bib, stalker) {
          return $http.put(updateStalker(bib), {operation: "remove", stalker: stalker});
        },

        getWithStalkersFrom: function(offset, limit) {
          return $http.get(database + "/_design/admin-ng/_view/with-stalkers?skip=" + offset +
             "&limit=" + limit);
        }

      };
    }]);
