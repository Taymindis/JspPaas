# The example of inherited annotation in java


[ref](https://stackoverflow.com/questions/7761513/is-there-something-like-annotation-inheritance-in-java/18585833#18585833)

```java
    @Target(value = {ElementType.ANNOTATION_TYPE})
    public @interface Vehicle {
    }

    @Target(value = {ElementType.TYPE})
    @Vehicle
    public @interface Car {
    }

    @Car
    class Foo {
    }
```
