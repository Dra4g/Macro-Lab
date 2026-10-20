# MacroLab Patterns

“Confermato” vuol dire che l’episodio soddisfa tutte le regole numeriche impostate
Il programma procede così:
1. Cerca una variazione forte dell’inflazione: almeno 1,5 punti percentuali in 3 mesi, nella direzione del pattern scelto. Per esempio, dal 2% al 3,5% è una salita di 1,5 punti.
2. Controlla tre mercati: EUR/USD, ETF BTP Italia ed EURO STOXX 50. Per ciascuno confronta il prezzo all’inizio di quei tre mesi con il minimo raggiunto entro 3 mesi dopo il segnale.
3. Applica queste condizioni:
4. | Condizione | Inflazione in salita | Inflazione in discesa |
   |---|---|---|
   | Movimento HICP in 3 mesi | Almeno +1,5 punti | Almeno −1,5 punti |
   | Discesa di ogni mercato dalla baseline | Almeno −3% | Almeno −3% |
   | Recupero dal rispettivo minimo | Non richiesto | Almeno +3% entro 6 mesi |

Devono passare tutti e tre i mercati. Se due scendono del 10% e il terzo scende del 2,8%, con soglia al 3% l’episodio è “Non confermato”. È proprio ciò che succede nel caso di luglio 2023: il BTP perde circa il 2,82%.
Un esempio semplice: prezzo iniziale 100, minimo 96 → discesa del 4%, quindi passa la soglia. Nel pattern con recupero deve poi arrivare almeno a 98,88, cioè il 3% sopra 96; non deve necessariamente tornare a 100.
Gli altri esiti significano:
- In attesa: non sono ancora disponibili tutti i mesi della finestra di verifica.
- Dati insufficienti: manca parte dello storico necessario.
- Non confermato: almeno un mercato, con finestra completa, non soddisfa le condizioni.