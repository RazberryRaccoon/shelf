# Getting ScreenScraper's developer credentials for Shelf

ScreenScraper (screenscraper.fr) is the only source Shelf has for box spines, cartridge, disc and card art, and gameplay
videos, and a second source for manuals. Its API asks every app for two things:

1. **The app's own developer credentials** (a developer ID and password), requested once by the app's maker. Shelf
   doesn't have these yet.
2. **Each person's own ScreenScraper account** (free). You enter yours in Shelf's Settings › Media › ScreenScraper.

Until the developer credentials arrive, Settings › Media shows ScreenScraper as "Waiting for Shelf's credentials".
Everything else works meanwhile.

## What to do

1. Make a free account at https://www.screenscraper.fr (Créer un compte, top right) and sign in.
2. Open the forum (Forum, in the top menu). Look for the section about the API and developer credentials. The site is
   in French; developer IDs are called *identifiants développeur*. If you can't find the section, post in the general
   help section and someone will point you to it.
3. Start a new topic with the message below (the English is fine; the French is there to help).
4. When the credentials arrive (usually a private message, often within a few days), tell Claude. Claude puts them in
   `Media.kt` (`SS_DEV_ID` and `SS_DEV_PASSWORD`), and ScreenScraper starts working for anyone using Shelf.
   You can also enter them yourself straight away, in Settings › Media › ScreenScraper › Developer ID and Developer
   password.

## The message

> **Subject:** Developer credentials request: Shelf (Android game launcher)
>
> Hello,
>
> I'm making Shelf, a free, personal Android game launcher (frontend) for retro handhelds like the Retroid Pocket.
> It shows each game as a 3D cartridge and box, and I'd like to use ScreenScraper for box art (front, back, spine),
> cartridge and disc art, title screens, screenshots, videos, game details and manuals.
>
> - Software name: Shelf
> - Platform: Android
> - Source: https://github.com/RazberryRaccoon/shelf
> - Use: one user at a time, looking up their own games, with each person's own ScreenScraper account
>   (ssid/sspassword), cached on the device so each game is looked up once
>
> Could I please have developer credentials (devid / devpassword) for it?
>
> Thank you!
>
> *Bonjour, je développe Shelf, un lanceur de jeux Android gratuit et personnel. Pourrais-je obtenir des identifiants
> développeur (devid / devpassword) pour l'API ? Merci !*
