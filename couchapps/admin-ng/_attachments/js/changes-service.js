angular.module("admin-ng").factory("changesService", ["database", "$http", "$httpParamSerializer", "$timeout",
    function(database, $http, $httpParamSerializer, $timeout) {

      var onChange = function(scope, params, callback) {
        var ev;
        var allParams = angular.merge({feed: "eventsource"}, params);

        // The reconnection delay after an error will start at 10ms
        // and will be doubled until it reaches 20.48s.
        var reconnectionDelay;

        var reconnect = function() {
          ev = new EventSource(database + "/_changes?" + $httpParamSerializer(allParams));
          ev.onopen = function() {
            reconnectionDelay = undefined;
          };
          ev.onmessage = function(event) {
            allParams.since = event.lastEventId;
            scope.$applyAsync(function() { callback(JSON.parse(event.data)); });
          };
          ev.onerror = function(event) {
            if (ev.readyState === EventSource.CLOSED) {
              if (!reconnectionDelay)
                reconnectionDelay = 10;
              else if (reconnectionDelay < 20480)
                reconnectionDelay *= 2;
              ev.close();
              $timeout(reconnect, reconnectionDelay, false);
            }
          };
        };

        reconnect();
        scope.$on("$destroy", function() { ev.close(); });
      };

      // Get the initial value of a document, install it in the given scope,
      // then check for updates. The promise is resolved when the first
      // value is installed.
      // The scope parameter must be a real scope since its `$onDestroy`
      // event will be watched.
      var installAndCheck = function(scope, name, docid) {
        return $http.get(database + "/" + docid + "?local_seq=true")
          .then(function(response) {
            scope[name] = response.data;
            onChange(scope, {filter: "_doc_ids", doc_ids: '["' + docid + '"]',
              include_docs: true, heartbeat: 30000, since: response.data._local_seq},
              function(change) { scope[name] = change.doc; });
          });
      };

      // Call `callback` with every element returned by the view `design/view`.
      // Once this is done, resolve the promise, and call `callback` with any
      // new document matching `design/view` used as a filter.
      // The scope parameter must be a real scope since its `$onDestroy`
      // event will be watched.
      var initThenOnChange = function(scope, design, view, callback) {
        return $http.get(database + "/_design/" + design + "/_view/" + view +
            "?include_docs=true&update_seq=true&reduce=false")
          .then(function(response) {
            scope.$applyAsync(function() { angular.forEach(response.data.rows, callback); });
            onChange(scope, {filter: "_view", view: design + "/" + view,
              since: response.data.update_seq,
              include_docs: true, heartbeat: 30000}, callback);
            return response;
          });
      };

      return {
        onChange: onChange,
        initThenOnChange: initThenOnChange,
        installAndCheck: installAndCheck
      };
    }]);

angular.module("admin-ng").factory("globalChangesService", ["changesService", "$rootScope", "database", "$http",
    function(changesService, $rootScope, database, $http) {
      return {
        start: function() {
          changesService.onChange($rootScope, {since: "now", heartbeat: 3000, include_docs: true},
              function(change) {
                $rootScope.$broadcast("change", change);
              });
        }
      };
    }]);
