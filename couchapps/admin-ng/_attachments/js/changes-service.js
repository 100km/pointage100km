angular.module("admin-ng").factory("changesService", ["database", "$http", "$httpParamSerializer", "$timeout", "$rootScope", "pubsub",
    function(database, $http, $httpParamSerializer, $timeout, $rootScope, pubsub) {

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
        return $http.get(database + "/" + docid)
          .then(function(response) {
            scope[name] = response.data;
            filterChanges(scope, function(change) { return change.doc._id === docid; },
                function(change) {
                  scope.$applyAsync(function() { scope[name] = change.doc; });
                });
          });
      };

      var globalChangesStart = function() {
        onChange($rootScope, {since: "now", heartbeat: 30000, include_docs: true},
            function(change) {
              pubsub.publish("globalChange", change);
            });
      };

      // Filter the global changes stream.
      // The scope parameter must be a real scope since its `$onDestroy`
      // event will be watched.
      var filterChanges = function(scope, filter, callback) {
        var id = pubsub.subscribe(function(key, element) {
          return key === "globalChange" && filter(element);
        }, callback);
        scope.$on("$destroy", function() { pubsub.unsubscribe(id); });
      };

      // Call a callback on the initial value returned by the view, then
      // a callback if a filter matches a change. The promise is completed
      // when the initial value has been sent to the callback.
      // The scope parameter must be a real scope since its `$onDestroy`
      // event will be watched.
      var initThenFilter = function(scope, design, view, initCallback, filter, changeCallback) {
        return $http.get(database + "/_design/" + design + "/_view/" + view +
            "?include_docs=true&reduce=false")
          .then(function(response) {
            initCallback(response.data);
            filterChanges(scope, filter, changeCallback);
          });
      };

      // Same than initThenFilter, except the same callback will be called
      // on every initial row and on the new changes.
      // The scope parameter must be a real scope since its `$onDestroy`
      // event will be watched.
      var initThenFilterEach = function(scope, design, view, filter, callback, apply) {
        return initThenFilter(scope, design, view,
            function(data) {
              if (apply)
                scope.$applyAsync(function() { angular.forEach(data.rows, callback); });
              else
                angular.forEach(data.rows, function(row) { callback(row); });
            },
            filter,
            function(row) {
              if (apply)
                scope.$applyAsync(function() { callback(row); });
              else
                callback(row);
            });
      };

      return {
        initThenFilter: initThenFilter,
        initThenFilterEach: initThenFilterEach,
        installAndCheck: installAndCheck,
        globalChangesStart: globalChangesStart,
        filterChanges: filterChanges
      };
    }]);
