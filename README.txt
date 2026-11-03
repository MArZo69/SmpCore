SmpCore - module /rankup
========================

COMPILÉ avec Paper 26.2 (build réussi), mais pas encore testé en jeu.
S'il y a une erreur au démarrage, envoie-la telle quelle.

1) COMPILER (sur ton PC)
   - Installer Java 25 (JDK Temurin 25) ET Gradle 9.1 ou plus récent
     (Java 25 ne marche pas avec les Gradle plus anciens).
       Windows : winget install Gradle.Gradle      (ou https://gradle.org/install)
       Linux   : sdk install gradle                (SDKMAN)
     Vérifie avec :  java -version   et   gradle --version
   - Dans ce dossier :
       gradle build
   - Le plugin est créé dans :  build/libs/SmpCore-0.1.0.jar
   - Au premier lancement, Gradle télécharge l'API Paper, VaultAPI et l'API LuckPerms.
   - Autre possibilité : ouvrir ce dossier dans IntelliJ IDEA (Gradle intégré) puis
     lancer la tâche "build".

2) INSTALLER
   - Mettre SmpCore-0.1.0.jar dans plugins/
   - Redémarrer le serveur (pas de /reload).
   - Les fichiers plugins/SmpCore/config.yml et messages.yml sont créés au premier démarrage.
   - Prérequis déjà présents chez toi : Vault (VaultUnlocked), LuckPerms, Essentials.

3) UTILISER
   /rankup            -> ouvre la liste des grades (du plus petit au plus grand)
  clic sur un grade   -> écran avec LureCoin à gauche et argent IG à droite
   /rankup confirm    -> achète directement le prochain grade (compatibilité)
  /boutique          -> ouvre les catégories Grades, Clés et Boosts
  /coins             -> affiche ton solde LureCoins
   /smpcore reload    -> recharge config.yml et messages.yml  (permission smpcore.admin)

4) PRIX  (config.yml, section rankup.grades)
   Aventurier 0 | Héros 500000 | Légende 1500000 | Suprême 5000000 | Divin 15000000
   Prix de chaque passage, non cumulés. Modifiable sans recompiler.
  Prix boutique LureCoins (boutique.grades) : Héros 500, Légende 1500,
  Suprême 2250, Divin 2700.
   IMPORTANT : les "id" doivent être les noms exacts des groupes LuckPerms.
   Le groupe "default" s'affiche "Aventurier" (c'est ton grade de départ).
   Le premier grade est le grade de départ, son prix n'est jamais demandé.

5) COMMENT ÇA MARCHE
   - Le grade d'un joueur = le groupe direct le plus haut de la liste dans LuckPerms.
   - Au rankup : les groupes de la liste sont retirés, le nouveau est ajouté.
     Les groupes staff (helpeur, moderateur, owner...) ne sont jamais touchés.
   - Si LuckPerms échoue après le paiement, l'argent est remboursé.

6) TEST CONSEILLÉ (avec un compte SANS OP)
   a) /eco give <pseudo> 600000
  b) /rankup -> clic sur Héros -> fleur à droite -> doit devenir Héros, solde 100000
   c) /lp user <pseudo> info           -> groupe heros, plus default
  d) /coins give <pseudo> 500, puis acheter Légende uniquement après Héros

7) PERSONNALISATION
   - Les textes du menu sont dans messages.yml, section "rankup-gui".
   - Les avantages affichés au survol sont dans config.yml, section
     rankup.grades[].advantages (MiniMessage).
   - Le message d'annonce utilise {player} et {next} (PAS <Grade.rankup>).
   - Si un nouveau messages.yml est fourni, les clés absentes de ton fichier
     prennent automatiquement les valeurs par défaut du plugin.

8) BOUTIQUE TEBEX
   - Dans chaque package LureCoins, mets : coins give {username} <montant>
   - Le joueur achète ensuite ses grades via /rankup ou /boutique.
   - Les grades sont verrouillés tant que le grade précédent n'est pas possédé,
     que le paiement soit en monnaie IG ou en LureCoins.
   - Les catégories Clés et Boosts sont prévues mais pas encore achetables.
   - Les instructions Tebex sont dans pret-a-installer/TEBEX-GRADES.md.
