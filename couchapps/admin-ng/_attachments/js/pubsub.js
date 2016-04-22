angular.module("pubsub", []).factory("pubsub", [
    function() {
      var latestSubscriptionId = 0;
      var subscribers = {};

      return {
        subscribe: (filter, callback) => {
          latestSubscriptionId++;
          subscribers[latestSubscriptionId] = {filter: filter, callback: callback};
          return latestSubscriptionId;
        },

        unsubscribe: subscriptionId => delete subscribers[subscriptionId],

        publish: (key, value) => {
          var count = 0;
          angular.forEach(subscribers, subscriber => {
            if (subscriber.filter(key, value)) {
              count++;
              subscriber.callback(value, key);
            }
          });
          return count;
        }
      };
    }]);
