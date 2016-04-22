angular.module("admin-ng").factory("changesService", ["database", "$http", "$httpParamSerializer", "$timeout", "$rootScope", "pubsub",
    function(database, $http, $httpParamSerializer, $timeout, $rootScope, pubsub) {

      var onChange = (scope, params, callback) => {
        var ev;
        var allParams = angular.merge({feed: "eventsource"}, params);

        // The reconnection delay after an error will start at 10ms
        // and will be doubled until it reaches 20.48s.
        var reconnectionDelay;

        var reconnect = () => {
          ev = new EventSource(database + "/_changes?" + $httpParamSerializer(allParams));
          ev.onopen = () => reconnectionDelay = undefined;
          ev.onmessage = event => {
            allParams.since = event.lastEventId;
            scope.$applyAsync(function() { callback(JSON.parse(event.data)); });
          };
          ev.onerror = event => {
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
        scope.$on("$destroy", () => ev.close());
      };

      // Get the initial value of a document, install it in the given scope,
      // then check for updates. The promise is resolved when the first
      // value is installed.
      // The scope parameter must be a real scope since its `$onDestroy`
      // event will be watched.
      var installAndCheck = (scope, name, docid) =>
        $http.get(database + "/" + docid)
          .then(response => {
            scope[name] = response.data;
            filterChanges(scope, change => change.doc._id === docid,
                  change => scope.$applyAsync(() => scope[name] = change.doc))
          });

      var globalChangesStart = () =>
        onChange($rootScope, {since: "now", heartbeat: 30000, include_docs: true},
            change => pubsub.publish("globalChange", change));

      // Filter the global changes stream.
      // The scope parameter must be a real scope since its `$onDestroy`
      // event will be watched.
      // The scope parameter must be a real scope since its `$onDestroy`
      // event will be watched.
      var filterChanges = (scope, filter, callback) => {
        var id = pubsub.subscribe((key, element) => key === "globalChange" && filter(element),
            callback);
        scope.$on("$destroy", () => pubsub.unsubscribe(id));
      };

      // Filter the global changes stream after a sequence number which is
      // given in a promise. Before that, changes are accumulated and will
      // be replayed if they are greater than the sequence number.
      // The promise returned by this function will resolve once the
      // sequence number has been resolved.
      // The scope parameter must be a real scope since its `$onDestroy`
      // event will be watched.
      var filterChangesAfter = (scope, filter, callback, after) => {
        var prematureChanges = [];
        var afterResolved;
        filterChanges(scope, filter,
            change => {
              if (afterResolved === undefined)
                prematureChanges.push(change);
              else if (change.seq > afterResolved)
                callback(change);
            });
        return after.then(value => {
          afterResolved = value;
          angular.forEach(prematureChanges, change => {
            if (change.seq > afterResolved)
              callback(change);
          });
          prematuresChanges = undefined;
        });
      };

      // Call a callback on the initial value returned by the view, then
      // a callback if a filter matches a change. The promise is completed
      // when the initial value has been sent to the callback.
      // The scope parameter must be a real scope since its `$onDestroy`
      // event will be watched.
      var initThenFilter = (scope, design, view, initCallback, filter, changeCallback) =>
        filterChangesAfter(scope, filter, changeCallback,
            new Promise((resolve, fail) => 
              $http.get(database + "/_design/" + design + "/_view/" + view +
                "?include_docs=true&reduce=false&update_seq=true")
              .then(response => {
                initCallback(response.data);
                resolve(response.data.update_seq);
              })));

      // Same than initThenFilter, except the same callback will be called
      // on every initial row and on the new changes.
      // The scope parameter must be a real scope since its `$onDestroy`
      // event will be watched.
      var initThenFilterEach = (scope, design, view, filter, callback, apply) =>
        initThenFilter(scope, design, view,
            data => {
              if (apply)
                scope.$applyAsync(function() { angular.forEach(data.rows, callback); });
              else
                angular.forEach(data.rows, function(row) { callback(row); });
            },
            filter,
            row => {
              if (apply)
                scope.$applyAsync(function() { callback(row); });
              else
                callback(row);
            });

      return {
        initThenFilter: initThenFilter,
        initThenFilterEach: initThenFilterEach,
        installAndCheck: installAndCheck,
        globalChangesStart: globalChangesStart,
        filterChanges: filterChanges,
        filterChangesAfter: filterChangesAfter
      };
    }]);
