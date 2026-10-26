# EECS3311 Study Notes: UML Diagrams

UML, the Unified Modeling Language, is a standard graphical notation for describing the structure and behaviour of software. It is independent of the programming language, so the same diagram can be implemented in Java or C++. UML is not a process: it tells you how to draw a design, not how to run a project.

## Class diagrams

A class diagram shows the classes of a system, their attributes and operations, and the relationships between them. Each class is a box with three compartments: the name, the attributes and the operations. Visibility is shown with symbols: plus means public, minus means private, the hash sign means protected and the tilde means package visibility. Static members are underlined and abstract class names are written in italics. An interface is marked with the stereotype interface.

An association is a solid line meaning that one class holds a reference to another. Multiplicity shows how many objects take part, such as 1, 0..1 or 0..*. Aggregation is drawn with a hollow diamond and means a whole groups parts that can exist on their own. Composition is drawn with a filled diamond and means the parts cannot exist without the whole. Inheritance, also called generalization, is a solid line with a hollow triangle pointing at the parent class. Realization is a dashed line with a hollow triangle pointing at the interface being implemented. A dependency is a dashed arrow meaning one class uses another temporarily.

## Use-case diagrams

A use-case diagram shows the actors outside the system and the use cases they participate in. An actor is a role, which can be a person or another system such as an external web service. A use case is drawn as an ellipse inside the system boundary and describes something an actor wants to achieve. Each use case is usually documented with a description that lists the preconditions, the main success scenario, alternative flows and postconditions.

## Sequence diagrams

A sequence diagram shows how objects collaborate over time to carry out one scenario. Each participant has a lifeline, a vertical dashed line, and time runs from top to bottom. A synchronous message is a solid line with a filled arrowhead, and a return message is a dashed line with an open arrowhead. An activation bar on a lifeline shows when an object is executing. Combined fragments draw boxes around groups of messages: alt shows alternative branches with guard conditions, opt shows an optional part, and loop shows repetition. Guards are written in square brackets, for example [quiz has questions].

## Activity and state diagrams

An activity diagram models a workflow with actions, decisions and parallel branches, similar to a flowchart. A state diagram shows the states an object can be in and the events that cause transitions between them, which is useful for objects with a clear life cycle such as an order or a connection.
