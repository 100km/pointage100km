# Application pour le pointage d'une course à pied

## Spécifications

La course est bien réelle [Les 100 kms à pied de Steenwerck](http://100kmsteenwerck.fr/) et l'application sera utilisée dès la prochaine édition (mai 2012).

La course se déroule en 5 tours de 20 kms. Il y a 3 postes de pointage par courses, les coureurs ont un dossard et des bénévoles s'occupent d'entrer leur dossard dans un PC. Il s'agit de faire l'application pour enregistrer les passages.

### Partie locale

- afficher le nom du coureur, sa position, son numéro de tour
- afficher son retard sur le concurrent précédent
- (rechercher un concurrent passé plus tôt)
- doit être décorélée de la partie connectée (perte de la connexion régulièrement)

### Partie distribuée

- mettre à jour les temps de passage sur un serveur central
 - il faut vérifier que les données soient bien arrivées sur le serveur
 - il faut pouvoir se réveiller automatiquement quand la connexion internet revient
- faire de la correction d'erreur, avec messages d'alertes si un concurrent a loupé un pointage

## Implémentation : les grandes lignes

Le projet va utiliser [couchDB](http://couchdb.apache.org/) comme infrastructure principale. CouchDB est capable de se répliquer sur plusieurs serveur de façon fiable et autonomes. Nous aurons donc une base de données CouchDB sur chaque site. CouchDB s'occupe de répliquer les bases de données sur chaque site dès que cela est possible. *L'avantage est que tout passe par le protocole http et ne sera pas bloqué par une clé 3G.*

La base données des temps de passages sera très simple : `Site ID | Transaction ID | Type de transction | Numéro de dossard | Temps de passage`. Pour simplifier énormément les problèmes de cohérence, on fera une BDD en 'append only'. Si jamais, une transaction s'avère fausse, on entrera une nouvelle transaction chargée d'annuler une transaction précédente.

Voici un schéma de l'architecture du projet :

![Architecture](https://github.com/100km/pointage100km/raw/master/doc/Architecture_pointage.png)

Sur le serveur, il y aura un [broker](http://en.wikipedia.org/wiki/Message_broker) chargé de récupérer les messages AMQP provenant des différents sites. Le serveur maintient une base de données globale. Le serveur va offrir une interface de type publish/subscribe pour le site web de l'association et pour d'éventuelles applications mobiles.

Sur chaque site, l'interface utilisateur principale sera une WebApp (écrite en Lift) qui permettra de recevoir les numéros de dossard des concurrents et d'afficher les temps de passages des concurrents. L'application Main récupère l'information du dossard et l'enregistre sur sa BDD locale. Elle envoie également un message par AMQP au serveur. Si Internet est disponible le message part immédiatement, sinon il est délayé. De plus, comme la connexion peut-être vraiment aléatoire, il y a un second système de mise à jour par clé USB. Si une clé USB est branchée sur un des sites, l'application Main copie sa BDD locale sur la clé (ou la met à jour si la clé USB est déjà passée sur le site). L'application récupère également les informations des autres sites si elles sont présentes sur la clé USB. *L'application renvoie toutes ces informations nouvelles au serveur*. Le serveur sera capable de savoir si il a déjà reçu une transaction et ne la doublera pas dans la BDD globale. Ainsi, il suffira d'un seul site connecté pour mettre à jour le serveur (si une personne tourne avec une clé USB). On peut aussi imaginer un site supplémentaire toujours connecté qui permet de mettre à jour le serveur.

## Répartition des tâches

### REPLICA

Tester la réplication (avec des “gros volumes”) + programme qui relance la synchro quand on a la connection

- Assignee : Victor + Sam

### USB

Programme de gestion de la clé USB (bash ? python ? scala ?)

- Assignee : Victor + Sam

### POINTAGE

HTML + javascript pour l'application de saisie + affichage du classement local

- Assignee : Jon

### VIDEOPROJ

HTML + javascript pour le vidéo projecteur (ou Lift)

- Assignee : Sebastian

### MOBILE + WEBSITE

Cette section concerne toutes les informations à afficher sur le site WEB (ATTENTION: doit être affichable par les mobiles).

Les fonctionnalités :

- classement en temps réel (top 10)
- recherche concurrent (optionnal: affichage de la position théorique)

HTML + javascript pour application mobile (CSS, less, media queries)

- Assignee : Victor, Florian, Tomô

## 1er point : 5/12/11

Ordre du jour :

- couchDB : points forts et points faibles
- couchapp : qu'est-ce qu'on a fait ?
- gestion des tours

### I - couchDB

- version avec encore beaucoup d'incompatibilités
- on travaille avec la 1.1.1
- version < 1.3 ⇒ problème avec les design documents en attachment qui contiennent des /
- on doit essayer l'authentification avec les certificats sur la 1.1.1
- A tester : base _replicator : comportement par rapport a la perte de connection
- A tester : documents _local : enregistrements locaux pour configurer les différents sites (noms, ID du site, chemin du certificat)
- import/export sur clé USB : 2 solutions
 - fichier ZIP généré par du java/scala (préféré)
 - fichier sqlite

### II - couchapp

- on peut
 - insérer une chaîne arbitraire dans la base steenwerck100km de couchapp
 - trouver le numéro du tour d'un mec automatiquement (se base juste sur le nombre d'entrée)
- problème de synchronisation
 - Attention a rendre le double input plus difficile
 - Solution: implementer un –force, ajouter des boutons d'annulation pour faciliter l'administration

### III - gestion des tours

Il faut faire un choix entre :

- stocker le numéro de tours dans la BDD
- calculer le numéro de tours à chaque fois qu'on en a besoin

Il est beaucoup plus simple d'avoir le numéro de tour dans la BDD pour les classements et les différents affichages. Donc on penche plutôt pour cette solution.

On ferait donc un document par dossard qui contient le nom du concurrent, sa course et tous ses temps de passages. Pour résoudre les conflits, on va lancer une nouvelle instance de couchDB qui va utiliser la BDD de la clé USB puis lancer une réplication entre la BDD locale et la nouvelle instance. On essaie ensuite de lancer une résolution de conflit.

### IV - Site Web

On abandonne l'idée de LIFT et on part plutôt sur une couchapp avec des CSS qui vont bien pour les mobiles.
