# SCAN — Téléphone + recharge Madagascar

Application Android qui remplace l'application Téléphone du système et y intègre le
scan des tickets de recharge, les forfaits, le partage de crédit et l'envoi de SMS
groupés — sans jamais avoir à changer d'application.

## Fonctionnalités

### Téléphone complet
- Clavier de numérotation avec recherche T9 dans les contacts.
- Journal d'appels (entrants, sortants, manqués), contacts et favoris.
- Écran d'appel plein écran : répondre, raccrocher, muet, haut-parleur, mise en
  attente, clavier DTMF, affichage sur l'écran verrouillé.
- L'application peut être définie comme **application Téléphone par défaut**
  (`ROLE_DIALER` + `InCallService`), et répond aux intentions `tel:` / `DIAL`.

### Scan de crédit
- Lecture du code de recharge par la caméra (ML Kit), détection automatique de
  l'opérateur à partir du texte du ticket.
- Longueur du code propre à chaque opérateur (14 chiffres, 15 pour Airtel…),
  configurable.
- Garde-fous : code suspect, code déjà utilisé dans les dernières 24 h, pause de
  3 secondes entre deux envois.
- Exécution du code USSD **dans l'application** quand l'appareil le permet
  (Android 8+), avec affichage de la réponse de l'opérateur.

### Cartes SIM
- Toutes les cartes actives sont listées avec leur emplacement, leur opérateur et
  le nom que vous leur donnez.
- **Plusieurs cartes du même opérateur sont gérées** : l'application ne devine
  jamais toute seule. Une seule carte correspond → elle est utilisée ; plusieurs
  cartes correspondent → le sélecteur s'ouvre, et votre choix peut être mémorisé
  par opérateur. La carte utilisée est toujours affichée avant l'opération.
- Routage intelligent : un appel, un transfert ou un SMS part de la carte du même
  opérateur que le numéro destinataire lorsqu'elle est présente.

### Messagerie complète
Trois onglets réunis au même endroit — **Réception**, **Groupé**, **Contacts** :
- **Boîte de réception** : conversations triées par date, compteur de non-lus,
  fil de discussion avec bulles, réponse directe, suppression. La liste se met à
  jour toute seule quand un message arrive (observateur sur la base SMS).
- L'application peut être définie comme **application SMS par défaut** : elle
  reçoit alors `SMS_DELIVER`, enregistre elle-même les messages entrants, notifie
  l'utilisateur, et propose la réponse rapide par message pendant un appel.
- **Envoi groupé** : sélection multiple de contacts, ajout de numéros à la main,
  modèles réutilisables, personnalisation par `{NAME}` et `{NUMBER}`, envoi
  séquentiel avec progression et rapport d'échecs.
- Répartition automatique par opérateur : chaque destinataire est servi par la
  carte SIM de son propre réseau quand elle est disponible.
- **Contacts** : la même liste que dans l'onglet Téléphone, pour démarrer une
  conversation ou un appel sans changer d'application.

### Forfaits et partage
- Les forfaits des trois opérateurs sont livrés avec l'application, puis stockés
  en base : chacun peut être **modifié, ajouté, supprimé** ou mis en favori.
- Achat pour soi, ou envoi du forfait à un autre numéro.
- Écran de partage de crédit : l'opérateur du destinataire est reconnu par son
  préfixe, la SIM correspondante est choisie, le code complet est affiché puis
  confirmé avant d'être composé. Une file d'attente permet d'enchaîner plusieurs
  destinataires.

### Tout est modifiable
Depuis l'écran Réglages, pour chaque opérateur :
code de recharge, nombre de chiffres, code de solde, code de transfert de crédit,
code de partage de forfait, numéro du service client, préfixes, mots-clés de
reconnaissance, couleur, et le fait de composer avec ou sans `*` et `#`.
Les codes des forfaits se modifient directement sur chaque fiche.

> Les codes de transfert et de partage livrés par défaut doivent être vérifiés
> auprès de votre opérateur : l'application affiche un avertissement et demande
> une confirmation tant que vous ne les avez pas validés.

## Langues
Malagasy, français et anglais, changeables à tout moment depuis n'importe quel écran.

## Permissions demandées
Caméra (scan), téléphone et appels, état et numéro des SIM, contacts, journal
d'appels, envoi et lecture des SMS, notifications (appel entrant, message reçu).

## Construire

**Prérequis :** [Android Studio](https://developer.android.com/studio)

1. Ouvrir le projet dans Android Studio.
2. Créer un fichier `.env` à la racine (voir `.env.example`).
3. Lancer sur un appareil physique — un émulateur n'a ni carte SIM ni USSD.

En ligne de commande :

```bash
./gradlew :app:assembleDebug     # APK de test
./gradlew :app:testDebugUnitTest # tests de la logique (numéros, codes, SIM)
```

La CI GitHub (`.github/workflows/android.yml`) produit l'APK signé de release sur
`main`.

## Structure

```
data/        entités Room (opérateurs, forfaits, SIM, modèles SMS), valeurs d'usine, réglages
telephony/   SIM, routage, appels, USSD, contacts, journal d'appels, SMS, service d'appel
ui/          écrans Compose : phone/, messages/, share/, settings/, call/, scan, forfaits
viewmodel/   état partagé entre les écrans
```

Base de données en version 2 : une migration conserve les opérateurs déjà
configurés et importe les codes de forfaits personnalisés de l'ancienne version.
