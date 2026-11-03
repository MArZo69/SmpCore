# LureSMP — Packs de LureCoins (Tebex)

Les LureCoins s'achètent **uniquement sur Tebex**. Aucun achat de LureCoins
n'est possible en jeu : `/boutique` ne fait que les dépenser.

## 1. Commande à mettre dans Tebex

Dans chaque package de LureCoins, ajoute une commande **exécutée en console** :

```text
/coin give {username} <montant>
```

- `{username}` est remplacé automatiquement par Tebex.
- `<montant>` est le nombre de LureCoins du pack (nombre entier, sans espace).
- La commande fonctionne même si le joueur est hors ligne, à condition qu'il se
  soit déjà connecté au moins une fois au serveur.
- Ne mets pas le `/` si ton panneau Tebex l'ajoute déjà : `coin give ...` suffit.

Exemple pour un pack de 3000 LureCoins :

```text
/coin give {username} 3000
```

## 2. Packs conseillés

Les montants ci-dessous collent aux prix de la boutique. Le prix en euros est à
définir dans Tebex ; le plugin ne gère que le crédit des LureCoins.

| Pack Tebex | LureCoins | Commande à configurer |
|---|---:|---|
| Starter | 700 | `/coin give {username} 700` |
| Bonus | 1800 | `/coin give {username} 1800` |
| Premium | 3000 | `/coin give {username} 3000` |
| Élite | 5000 | `/coin give {username} 5000` |
| Ultime | 10000 | `/coin give {username} 10000` |

À quoi correspondent ces montants :

| Achat en jeu | Coût |
|---|---:|
| LureBox x1 | 700 |
| LureBox x3 | 1800 |
| LureBox x5 | 3000 |
| SpawnerBox x1 | 900 |
| SpawnerBox x3 | 2500 |
| SpawnerBox x5 | 4000 |
| Grade Héros | 500 |
| Grade Légende | 1500 |
| Grade Suprême | 2250 |
| Grade Divin | 2700 |

## 3. Ce qui se passe après l'achat

1. Tebex exécute `/coin give <joueur> <montant>` en console.
2. Le solde est enregistré dans `plugins/SmpCore/coins.yml`.
3. Le chat reçoit :
   `Merci <joueur> d'avoir acheté <montant> LureCoins pour soutenir le serveur !`
4. Le joueur dépense ses LureCoins dans `/boutique` :
   - **Grades** : achat avec confirmation, puis application LuckPerms.
   - **Clés** : achat immédiat, la clé est donnée via
     `/crates give physical <crate> <quantité> <joueur>`.
   - Chaque achat de clé est annoncé dans le chat.

Les grades ne peuvent pas être sautés : il faut posséder le grade précédent.
Les LureCoins ne sont pas convertibles en argent IG et ne s'échangent pas.

## 4. Tester un pack

1. Crée un package de test à 1 € avec la commande
   `/coin give {username} 100`.
2. Achète-le avec un compte de test.
3. Vérifie dans la console la ligne d'exécution de la commande.
4. En jeu : `/coin` doit afficher `100 LureCoins`, et le message de
   remerciement doit apparaître dans le chat.
5. Supprime le package de test.

## 5. Dépannage

| Problème | Cause / solution |
|---|---|
| Le joueur n'est pas crédité | Vérifie que la commande Tebex est bien en **console** et que le joueur s'est déjà connecté au moins une fois. |
| `Joueur introuvable` | Le pseudo n'a jamais joué sur le serveur, ou il est mal orthographié. |
| Solde perdu après redémarrage | `plugins/SmpCore/coins.yml` a été supprimé ou le serveur n'a pas été arrêté proprement. |
| Aucun message de remerciement | Le fichier `messages.yml` du serveur est ancien : remplace-le par celui fourni (clé `coins.thanks`). |
| `/coin` inconnue | L'ancien jar est encore en place, ou le serveur n'a pas été redémarré complètement. |
| Achat de clé impossible | LureCrates (CrazyCrates renommé) doit exposer `/crates give physical <crate> <quantité> <joueur>`. Vérifie le nom exact des crates (fichiers dans `plugins/LureCrates/crates/`) et `config.yml > boutique.keys > crate`. |

## 6. Sauvegarde

Sauvegarde régulièrement `plugins/SmpCore/coins.yml` : c'est le seul fichier
qui contient les soldes des joueurs.
