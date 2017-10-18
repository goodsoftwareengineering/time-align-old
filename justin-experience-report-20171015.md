# Experience Report
## bugs
- [x] update period needs to check for no matching period by id
- [x] form buttons need to update nav (should handlers dispatch?)
- [x] navigating to add sometimes loads another entity

## enhancements
- deselct button under action pop up
- play button on ticker when selected (icon in the color of the selected task)
- calendar view (remove stats)
  - past days render actual as layers (relative?)
  - relative could mean most is all and least is 25%?
  - days in the future render planned
  - today renders planned
  - selected is white
- context headers for add/edit
- add button at the top of each drop down in list page section
- put add/edit in side drawer

## code
- move all state to app-db
  - color picker category edit form
  - bottom panel 
- handlers that route as a side effect instead of dispatching set active page
  - reg-event-rt
    - {:route [:add "/new/path/to/push"]}
    - {:route [:back]}
