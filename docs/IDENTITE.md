# Identité visuelle de SCAN

Ce document décrit ce à quoi ressemble SCAN et pourquoi. Il fait autorité sur les
couleurs, la typographie, les formes et la marque : tout écran ajouté à
l'application doit pouvoir s'y rattacher, sans nouvelle couleur codée en dur.

Le code correspondant vit dans `app/src/main/java/com/example/ui/theme/` :

| Fichier              | Contenu                                                        |
| -------------------- | -------------------------------------------------------------- |
| `Color.kt`           | toutes les couleurs de la palette, et elles seules              |
| `Theme.kt`           | les deux thèmes (clair, sombre) et les rôles propres à SCAN     |
| `Type.kt`            | l'échelle typographique et les styles code / clavier / montant  |
| `Shape.kt`           | les rayons d'arrondi                                            |
| `Brand.kt`           | la marque : `ScanLogo`, `ScanWordmark`, `ScanLockup`            |
| `ThemeController.kt` | le choix clair / sombre / système fait par l'utilisateur        |

---

## 1. Le principe qui décide de tout

**SCAN n'est pas un opérateur, et ne doit jamais en avoir l'air.**

L'application sert Yas (bleu nuit), Orange (orange) et Airtel (rouge) dans le
même écran. Si l'application prenait l'une de ces couleurs, l'utilisateur
croirait à chaque instant être « chez » cet opérateur — et enverrait un code sur
la mauvaise carte SIM.

D'où la règle :

- la couleur de l'application est un **indigo** qui n'appartient à personne ;
- la couleur d'un opérateur n'apparaît **que** dans la carte, la puce ou le
  bouton qui parle de cet opérateur, et elle vient toujours de sa configuration
  en base (`Operator.colorHex`), jamais d'une constante ;
- le vert veut dire « appeler » ou « réussi », le rouge « raccrocher » ou
  « problème ». Ces deux couleurs ne servent jamais à décorer.

## 2. Couleurs

### Marque

| Rôle             | Clair     | Sombre    | Usage                                       |
| ---------------- | --------- | --------- | ------------------------------------------- |
| `primary`        | `#4A3AD0` | `#C6C0FF` | l'application elle-même : titres, sélection |
| `primaryContainer` | `#E3DFFF` | `#3A28B9` | fonds d'accent, puce sélectionnée         |
| `secondary`      | `#8B5000` | `#FFB877` | l'ambre : le crédit, la valeur, le montant  |
| `tertiary`       | `#00696E` | `#81D3D9` | le réseau : cartes SIM, état de la ligne    |

### Sémantique (`MaterialTheme.scanColors`)

Material n'a pas de rôle « succès » ni « raccrocher ». SCAN en a besoin, donc ils
sont dans le thème et suivent le clair et le sombre comme les autres.

| Rôle             | Clair     | Sombre    | Usage                                          |
| ---------------- | --------- | --------- | ---------------------------------------------- |
| `success`        | `#1B6B3A` | `#8DD6A5` | appeler, répondre, recharge acceptée           |
| `warning`        | `#8A5300` | `#FFB951` | code à vérifier, modèle de transfert non validé |
| `hangUp`         | `#C62828` | `#E05252` | raccrocher, refuser                            |
| `error`          | `#BA1A1A` | `#FFB4AB` | échec, suppression, absence de SIM             |
| `cameraSuccess`  | `#4CAF50` | `#4CAF50` | par-dessus l'aperçu caméra — voir ci-dessous   |

`cameraSuccess` ne change pas entre clair et sombre : l'aperçu de la caméra est
noir dans les deux cas, donc la couleur qui s'y lit est la même.

### Fonds

Le fond n'est jamais blanc pur : `#FBF8FF` en clair, `#131318` en sombre — assez
neutre pour que les trois couleurs d'opérateur restent justes, assez teinté pour
appartenir à la famille indigo.

### Interdits

- pas de `Color(0xFF…)` dans un écran ; une couleur nouvelle se déclare dans
  `Color.kt` et s'expose dans un rôle ;
- pas de vert ni de rouge décoratif ;
- pas de dégradé sur un texte.

## 3. Typographie

La police système, volontairement : l'application doit rester légère, tourner sur
des téléphones d'entrée de gamme et se lire pareil en malgache, en français et en
anglais. L'identité vient du rythme, pas d'une police téléchargée.

- **Titres** : lourds et serrés (`ExtraBold`, interlettrage négatif).
- **Textes** : normaux, généreusement interlignés.
- **Étiquettes** : courtes, larges, en demi-gras.
- **Chiffres qui comptent** : monospace.

Les styles hors Material sont dans `ScanTextStyles` :

| Style          | Pour                                             |
| -------------- | ------------------------------------------------ |
| `code`         | un code USSD affiché ou confirmé                 |
| `codeLarge`    | les 14 ou 15 chiffres juste après un scan        |
| `dialpadInput` | le numéro en cours de composition                |
| `dialpadKey`   | le chiffre imprimé sur une touche                |
| `amount`       | un prix, un montant de crédit                    |
| `wordmark`     | SCAN                                             |
| `tagline`      | la ligne sous le nom                             |

Un code de recharge est **toujours** en monospace et espacé : deux chiffres ne
doivent jamais pouvoir être lus pour un seul.

## 4. Formes

Arrondis doux et généreux — les écrans sont faits de cartes qui portent un code,
un forfait ou un contact, et c'est l'arrondi qui les sépare, pas un trait.

| Rôle          | Rayon  | Exemple                        |
| ------------- | ------ | ------------------------------ |
| `extraSmall`  | 8 dp   | petites puces                  |
| `small`       | 12 dp  | puce SIM, bouton de choix      |
| `medium`      | 16 dp  | cartes                         |
| `large`       | 20 dp  | boîtes de dialogue             |
| `extraLarge`  | 28 dp  | feuilles, barre de navigation  |

## 5. La marque

### Le signe

Un viseur et la ligne qui le lit : quatre équerres, une ligne ambre, rien
d'autre. C'est le geste de l'application dessiné en une forme — on vise un ticket
de recharge, le cadre trouve le code, la ligne le lit.

Il existe en deux exemplaires qu'il faut modifier ensemble :

- `ui/theme/Brand.kt` → `ScanLogo()`, dessiné par Compose, pour l'intérieur de
  l'application ;
- `res/drawable/ic_scan_mark.xml`, pour l'icône de lancement et l'écran de
  démarrage ; `ic_scan_mark_mono.xml` pour l'icône thématique d'Android 13+ et
  `ic_notification.xml` pour la barre d'état.

### Le nom

**SCAN**, en capitales, très gras, largement interlettré (`ScanTextStyles.wordmark`).
Sous le nom, une ligne courte traduite dans les trois langues (clé `app_tagline`) :
*Fahana, antso, hafatra* / *Recharge, appels, messages* / *Top-up, calls, messages*.

### L'icône de lancement

Icône adaptative : fond indigo en dégradé (`#4A3AD0` → `#26109C`), signe en blanc,
ligne en ambre. Une version monochrome existe pour les icônes thématiques
d'Android 13+, et une version déjà détourée pour Android 7, qui ne connaît pas
les icônes adaptatives (`mipmap-anydpi/`).

### L'écran de lancement

Le signe centré sur l'indigo, sans animation : `res/drawable/launch_screen.xml`
pour Android 7 à 11, et les attributs `windowSplashScreen*` de
`values-v31/themes.xml` pour Android 12 et suivants. Le fond fonce en mode sombre.

### Les notifications

Toutes les notifications portent la couleur de la marque. L'icône, elle, dit
l'état et non l'application quand cela compte davantage : un appel entrant garde
le combiné du système — dans la barre d'état, un appel doit se lire comme un
appel — tandis qu'un message reçu porte le signe SCAN.

## 6. Clair et sombre

Trois choix dans **Réglages → Apparence** : suivre le téléphone, clair, sombre.
Le choix est appliqué immédiatement à toute l'application, écran d'appel compris,
et il est mémorisé.

**Les couleurs dynamiques d'Android 12+ sont désactivées par défaut.** Une
application qui prend la couleur du fond d'écran n'a plus d'identité, et surtout
elle brouille la distinction avec les couleurs d'opérateur. L'utilisateur peut
les activer depuis le même écran ; c'est son choix, pas le nôtre.

## 7. Ajouter un écran

1. Prendre les couleurs dans `MaterialTheme.colorScheme` et
   `MaterialTheme.scanColors`, jamais ailleurs.
2. Prendre les formes dans `MaterialTheme.shapes`.
3. Un code, un numéro, un montant : passer par `ScanTextStyles`.
4. Une couleur d'opérateur : passer par `OperatorColorUtils`, qui la lit dans la
   configuration modifiable par l'utilisateur.
5. Vérifier l'écran en clair **et** en sombre avant de le proposer.
