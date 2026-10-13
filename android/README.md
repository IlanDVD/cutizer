# Cutizer pour Android — Instagram

Application native Android 8.0 ou plus récent (API 26+), ciblant Android 16. Cette première version accompagne **uniquement l’application Instagram officielle**, `com.instagram.android`, dans le profil Android où Cutizer est installé.

## Installer et démarrer

1. Transférer `Cutizer-Android.apk` sur le téléphone et ouvrir le fichier pour l’installer. C’est un APK de test signé avec une clé de développement, pas une publication Google Play.
2. Ouvrir **Cutizer → Réglages**.
3. Activer **Reconnaître Instagram** : sur l’écran Android d’accès aux données d’utilisation, choisir Cutizer et autoriser l’accès.
4. Activer **Afficher Miso** : autoriser Cutizer à s’afficher au-dessus des autres applications. Selon le fabricant, Android présente une liste d’applications plutôt qu’un écran directement dédié à Cutizer.
5. Autoriser les notifications si souhaité, pour accéder aux commandes **Masquer** et **Arrêter**. Le refus n’empêche pas le service Android de fonctionner.
6. Appuyer sur **Démarrer le suivi d’Instagram**, puis ouvrir Instagram.

Si Instagram n’est pas installé, les essais d’animations restent disponibles. **Essais courts** réduit chaque animation à 12 secondes, sans ajouter de temps ni déclencher les paliers dans les statistiques.

## Comportement

| Instagram cumulé dans la journée | Animation |
| --- | --- |
| 30 minutes | Miso traverse le bas de l’écran de droite à gauche en 12 secondes. |
| 60 minutes | Descente en rappel au centre, regard insistant, puis remontée en 15 secondes. |
| 90 minutes | Apparition par le bas, bras croisés pendant 2 minutes, puis départ. |
| 120 minutes | Miso géant et sorts pendant 10 minutes, puis fondu de 2 secondes. |

Chaque palier apparaît une seule fois par jour. **Masquer** ferme immédiatement l’animation sans remettre le compteur à zéro. Quitter Instagram ou verrouiller l’écran ferme aussi un rappel automatique. Un essai reste visible sur les autres applications jusqu’à sa fermeture ou à sa fin, mais s’arrête au verrouillage.

Les trois premiers overlays laissent passer les gestes. Android 12+ limite l’opacité autorisée pour transmettre les touches : ils sont donc légèrement transparents. Le grand sort est opaque et reçoit les touches de l’écran recouvert ; son bouton **Masquer**, dans une fenêtre séparée, reste disponible. Aucune action n’est envoyée à Instagram et son contenu n’est pas modifié. Les barres système restent accessibles.

Le temps est mesuré environ chaque seconde, uniquement lorsque le suivi est activé, Instagram au premier plan et le téléphone déverrouillé. Une transition peut perdre une ou deux secondes ; aucune utilisation antérieure à l’activation n’est importée. Un Reel regardé sans toucher l’écran compte tant qu’Instagram reste au premier plan. Les interruptions longues ne sont pas ajoutées rétroactivement.

Le suivi utilise un service Android au premier plan avec une notification persistante. Il peut continuer après avoir quitté Cutizer. Après redémarrage du téléphone, arrêt forcé ou intervention du système/du fabricant, rouvrir Cutizer et démarrer le suivi si nécessaire. Aucun lancement automatique au démarrage ni contournement des restrictions de batterie.

## Vie privée et limites

- Aucun compte, serveur, permission Internet ou service d’accessibilité.
- Miso utilise les événements d’activité Android pour reconnaître Instagram ; il ne lit pas les publications, messages, identifiants ou captures d’écran.
- Seuls les totaux quotidiens et les paliers affichés sont conservés, dans le stockage privé de l’application. Les noms des autres applications ne sont pas enregistrés. Historique de 30 jours et remise à zéro du compteur à minuit, selon le fuseau du téléphone.
- Les sauvegardes Android de ces données sont désactivées. Une désinstallation supprime les statistiques.
- Instagram Lite, les navigateurs, les applications clonées dans un autre profil et le temps d’écran des autres appareils ne sont pas couverts.
- En mode écran partagé ou selon les événements fournis par le fabricant, la détection du premier plan peut être approximative. Les écrans qui interdisent les overlays peuvent empêcher l’affichage de Miso.
- La publication Google Play n’est pas faite : le service `specialUse` et les autorisations spéciales devront être déclarés et examinés pour une distribution sur le store.

## Compiler

Prérequis : JDK 17, SDK Android avec `platforms;android-36` et `build-tools;35.0.0`. Définir `ANDROID_HOME` ou `sdk.dir` dans `local.properties` (non versionné).

Depuis ce dossier :

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Sur macOS/Linux : `bash gradlew testDebugUnitTest lintDebug assembleDebug`.

L’APK se trouve dans `app/build/outputs/apk/debug/app-debug.apk`. Le wrapper officiel vérifie le SHA-256 de Gradle 8.13 ; le plugin Android est fixé à 8.13.2. La clé debug sert seulement aux essais. Des builds effectués sur des machines différentes peuvent avoir des signatures différentes et nécessiter une désinstallation préalable ; une vraie clé de distribution sera à conserver pour des mises à jour pérennes.

Le workflow GitHub **Android APK** lance les tests, Android Lint et la compilation, puis produit l’artefact `cutizer-android` contenant l’APK et les rapports. Il peut être relancé manuellement depuis l’onglet Actions.

## Tester sur téléphone

Les tests unitaires vérifient les paliers, doublons, reconstruction du premier plan, verrouillage, changement de jour, changement d’heure et restauration. Ils ne remplacent pas ces contrôles sur un appareil :

1. Vérifier le parcours avec chaque autorisation refusée, puis accordée.
2. Démarrer le suivi, ouvrir Instagram pendant une minute, revenir à Cutizer et vérifier le compteur.
3. Vérifier que le compteur ne monte pas dans une autre application, en pause ou sur écran verrouillé.
4. Essayer les quatre animations et leur bouton **Masquer**, puis tester les durées complètes de 2 et 10 minutes.
5. Pendant une apparition courte, vérifier que l’on peut toujours faire défiler Instagram ; pendant le grand sort, vérifier que Masquer et les commandes système restent accessibles.
6. Faire pivoter l’écran pendant un essai, quitter Instagram pendant un rappel automatique, retirer une autorisation pendant le suivi et relancer le téléphone.

## Remplacer les dessins

Remplacer `app/src/main/res/drawable-nodpi/miso_happy.png` et `miso_judge.png` par des PNG transparents de cadrage comparable, puis reconstruire. Les mouvements sont dessinés dans `OverlayController.java`. Les prompts de génération du personnage provisoire sont conservés dans `../src/assets/PROMPTS.txt`.

## Références Android

- [Événements d’utilisation](https://developer.android.com/reference/android/app/usage/UsageEvents.Event)
- [Fenêtres overlay et transmission des touches](https://developer.android.com/reference/android/view/WindowManager.LayoutParams)
- [Service au premier plan de type specialUse](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use)
