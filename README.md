# break-roulette
A history-aware match generator for 1:1 coffee breaks

To run this, first enter the mail addresses of all the coffee break participants in the file `pool.txt` (one address per line). Then simply execute the `run.sh` script. This will output pairings between the participants. If there is an odd number of participants, one three-way group is formed.

If you can't run the batch script directly, execute the steps it performs manually: first, build the project using Gradle, then simply run the ` main()` method of the `BreakRoulette` class. This can be done either from the command line or by importing this as a Gradle project in your favorite IDE.

To maintain a history of previous pairings, the file `previous-pairs.csv` is automatically extended each time you run the tool. You can maintain this file in a git repository if you like, running the script will remind you to push the changed file to this repo.

If you have feature requests, bug reports, questions or comments, please reach out to me using `as@cqse.eu`.