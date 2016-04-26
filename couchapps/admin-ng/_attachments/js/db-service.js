angular.module("admin-ng").factory("dbService",
    ["$http", "database", function($http, database) {

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
        }

      };
    }]);
