angular.module("pubsub", []).factory("pubsub", [
    function() {
      var latestSubscriptionId = 0;
      var subscribers = {};

      return {
        subscribe: function(filter, callback) {
          latestSubscriptionId++;
          subscribers[latestSubscriptionId] = {filter: filter, callback: callback};
          return latestSubscriptionId;
        },

        unsubscribe: function(subscriptionId) {
          return delete subscribers[subscriptionId];
        },

        publish: function(key, value) {
          var count = 0;
          angular.forEach(subscribers, function(subscriber) {
            if (subscriber.filter(key, value)) {
              count++;
              subscriber.callback(value, key);
            }
          });
          return count;
        }
      };
    }]);
