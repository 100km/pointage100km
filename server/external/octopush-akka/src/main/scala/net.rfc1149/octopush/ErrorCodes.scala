package net.rfc1149.octopush

import scala.io.Source

object ErrorCodes {

  private val errorsFromDocumentation = """
000 	OK
100 	Requête POST absente.
101 	Mauvais identifiants.
102 	Votre SMS dépasse les 160 caractères.
103 	Il n'y a aucun destinataire à votre message.
104 	Vous n'avez pas assez de crédit (vérifiez le nombre de vos contacts, ainsi que le nombre de SMS nécessaires à l'envoi de votre texte).
105 	Vous n'avez pas assez de crédit, mais votre dernière commande est en attente de validation.
106 	Vous avez mal renseigné le Sender. 3 à 11 caractères, choisis parmi 0 à 9, a à z, A à Z. Ni accent, ni espace, ni ponctuation.
107 	Le texte de votre message est vide.
108 	Vous n'avez pas renseigné votre login.
109 	Vous n'avez pas renseigné votre mot de passe.
110 	Vous n'avez pas renseigné la liste de destinataires.
111 	Vous n'avez pas choisi de moyen de saisie de vos destinataires.
112 	Vous n'avez pas défini la qualité de votre message.
113 	Votre compte n'est pas validé. Connectez-vous sur Octopush et rendez-vous à la section "Accueil".
114 	Vous faites l'objet d'une enquête pour utilisation frauduleuse de nos services.
115 	Le nombre de destinataire est différent du nombre de l'un des paramètres que vous leur avez associés.
116 	L'option de publipostage ne fonctionne qu'en utilisant une liste de contact.
117 	Votre liste de destinataires ne contient aucun bon numéro. Avez-vous formaté vos numéros en les préfixant du format international ? Contactez-nous en cas de difficulté.
118 	Vous devez cocher l'une des deux cases pour indiquer si vous ne souhaitez pas envoyer de SMS de test ou si vous l'avez reçu et validé.
119 	Vous ne pouvez pas envoyer de SMS de plus de 160 caractères pour ce type de SMS.
120 	Un SMS avec le même request_id a déjà été envoyé.
121 	En sms Premium, la mention "STOP au XXXXX" (à recopier TELLE QUELLE, sans les guillemets, et avec les 5 X) est obligatoire et doit figurer dans votre texte (respecter les majuscules).
122 	En SMS Standard, la mention "no PUB=STOP" (à recopier telle quelle) est obligatoire et doit figurer dans votre texte (respecter les majuscules).
123 	Le champ request_sha1 est manquant.
124 	Le champ request_sha1 ne correspond pas. La donnée a été mal renseignée ou la trame contient une erreur : la requête est rejetée.
125 	Une erreur non définie est survenue. Merci de contacter le support.
126 	Une campagne SMS est déjà en attente de validation pour envoi. Vous devez la valider ou l'annuler pour pouvoir en lancer une autre.
127 	Une campagne SMS est déjà en attente d'estimation. Vous devez attendre que le calcul soit terminé pour en lancer une autre.
128 	Trop de tentatives effectuées. Vous devez recommencer une nouvelles campagne.
129 	Campagne en cours de construction.
130 	Campagne non marquée comme terminée.
131 	Campagne non trouvée.
132 	Campagne envoyée.
133 	Le numéro unique de tramme est déjà utilisé.
150 	Aucun pays n'a été trouvé pour ce préfixe.
151 	Le pays destinataire ne fait pas parti des pays desservis par Octopush.
152 	Vous ne pouvez pas envoyer de SMS Standard vers ce pays. Choisissez le SMS Premium.
153 	La route étant congestionnée, ce type de SMS ne permet pas un envoi immédiat. Si votre envoi est urgent, merci de bien vouloir utiliser un autre type de SMS.
201 	Option disponible uniquement sur demande. N'hésitez pas à en faire la demande d'accès si vous en avez besoin.
202 	L'email du compte que vous souhaitez créditer est incorrect.
203 	Vous avez déjà des tokens en cours d'utilisation. Vous ne pouvez avoir qu'une session d'ouvert à la fois.
204 	Vous avez indiqué un mauvais token.
205 	Le nombre de SMS que vous souhaitez transférer est trop bas.
206 	Vous ne pouvez pas réaliser de campagne pendant un transfer de crédit.
207 	Vous n'avez pas accès à cette fonctionnalité.
208 	Mauvais type de SMS.
209 	Vous n'êtes pas autorisé à envoyer des SMS à cet utilisateur.
210 	Cet email ne fait pas partie de vos sous comptes ni de vos utilisateurs affiliés.
300 	Vous n'êtes pas autorisé à gérer vos listes par API.
301 	Vous avez atteint le nombre maximum de listes.
302 	Une liste du même nom existe déjà.
303 	La liste ci-nommée n'existe pas.
304 	La liste est déjà pleine.
305 	Il y a trop de contacts dans la requête.
306 	L'actions demandée est inconnue.
308 	Erreur de fichier.
500 	Impossible d'effecuter l'action demandée
501 	Erreur de connexion. Merci de contacter notre support client.
""".stripMargin

  val errorMessage: Map[Int, String] =
    Source.fromString(errorsFromDocumentation).getLines().map(_.split(" ", 2)).collect {
      case Array(n, t) ⇒ n.toInt → t.trim.stripSuffix(".")
    }.toMap

}
