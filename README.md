# supermarket

Simulate stockers stocking items in a supermarket.

- There are 5 boxes.
- Each box has 100 random items (not all items in the box are the same).
- Each item has a location: the aisle it belongs to and the shelf it belongs on.
- There are 5 aisles.
- Each aisle has 3 shelves.
- Stockers pick a random box and select a random item from the box (this takes 1 second to do).
- The stocker then goes to the aisle (this takes 1 second to do).
- The stocker then places the item on the shelf (this takes 1 second to do).
- The process repeats until all boxes are empty.
- Only one stocker can be at a box at a time (all other stockers have to wait for the box to be available).
- Only two stockers can be in an aisle at a time (all other stockers have to wait to enter the aisle).
- Only one stocker can be stocking an item on a shelf (other stockers have to wait to stock an item on the same shelf).

Your goal is to find the optimal number of stockers needed to complete the task as fast as possible (too few stockers could result in a longer running time, too many stockers might ressult in lot of queued stockers trying to get to a box, aisle, and shelf).
