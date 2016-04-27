function MessagesController($scope, dbService, stateService, changesService) {
  stateService.installInfos($scope);

  this.messages = [];

  this.deleteMessage = dbService.deleteMessage;

  this.loadMessages = changesService.serializedFunFactory(() =>
    dbService.getMessages().then(response => {
        this.messages = response.data.rows.map(row => row.value).filter(msg =>
            this.siteId === undefined || this.siteId === msg.target);
    }));
  
  var resetNewMessage = () => this.newMessage = {siteId: "-1", text: ""};
  resetNewMessage();

  this.sendMessage = () => {
    var target = this.newMessage.siteId !== "-1" ? Number(this.newMessage.siteId) : undefined;
    var text = this.newMessage.text.trim();
    if (text.length > 0) {
      dbService.createMessage(text, target);
      resetNewMessage();
    }
  };

  this.$onInit = () => {
    this.includeTarget = this.siteId === undefined;
    if (!this.includeTarget)
      this.title = "Local messages";
    changesService.filterChanges($scope, change => change.doc.type === "message",
        this.loadMessages);
    this.loadMessages();
  };
}

angular.module("admin-ng").component("messages", {
  templateUrl: "partials/messages.html",
  bindings: {"siteId": "<?"},
  controller: MessagesController
});
