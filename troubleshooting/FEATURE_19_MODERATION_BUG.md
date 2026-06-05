

2026-06-05T05:02:12.322+02:00 ERROR 35568 --- [spring-ai-demo] [nio-8080-exec-1] o.a.c.c.C.[.[.[/].[dispatcherServlet]    : Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception [Handler dispatch failed: java.lang.StackOverflowError] with root cause

java.lang.StackOverflowError
at java.base/java.util.Collections$UnmodifiableCollection.contains(Collections.java:1068) ~[na:na]
at java.base/java.util.stream.ReduceOps$3.getOpFlags(ReduceOps.java:185) ~[na:na]
at java.base/java.util.stream.AbstractPipeline.evaluate(AbstractPipeline.java:234) ~[na:na]
at java.base/java.util.stream.ReferencePipeline.collect(ReferencePipeline.java:682) ~[na:na]
at com.github.victools.jsonschema.generator.AnnotationHelper.extractAnnotationsFromMetaAnnotations(AnnotationHelper.java:123) ~[jsonschema-generator-5.0.0.jar:na]
at com.github.victools.jsonschema.generator.AnnotationHelper.resolveNestedAnnotations(AnnotationHelper.java:107) ~[jsonschema-generator-5.0.0.jar:na]
at com.github.victools.jsonschema.generator.AnnotationHelper.resolveAnnotation(AnnotationHelper.java:80) ~[jsonschema-generator-5.0.0.jar:na]
at com.github.victools.jsonschema.generator.TypeContext.getAnnotationFromList(TypeContext.java:279) ~[jsonschema-generator-5.0.0.jar:na]
at com.github.victools.jsonschema.generator.TypeContext.lambda$getTypeWithAnnotation$1(TypeContext.java:367) ~[jsonschema-generator-5.0.0.jar:na]
at java.base/java.util.stream.ReferencePipeline$2$1.accept(ReferencePipeline.java:178) ~[na:na]
at java.base/java.util.Spliterators$ArraySpliterator.tryAdvance(Spliterators.java:1034) ~[na:na]
at java.base/java.util.stream.ReferencePipeline.forEachWithCancel(ReferencePipeline.java:129) ~[na:na]
at java.base/java.util.stream.AbstractPipeline.copyIntoWithCancel(AbstractPipeline.java:527) ~[na:na]
at java.base/java.util.stream.AbstractPipeline.copyInto(AbstractPipeline.java:513) ~[na:na]
at java.base/java.util.stream.AbstractPipeline.wrapAndCopyInto(AbstractPipeline.java:499) ~[na:na]
at java.base/java.util.stream.FindOps$FindOp.evaluateSequential(FindOps.java:150) ~[na:na]
at java.base/java.util.stream.AbstractPipeline.evaluate(AbstractPipeline.java:234) ~[na:na]
at java.base/java.util.stream.ReferencePipeline.findFirst(ReferencePipeline.java:647) ~[na:na]
at com.github.victools.jsonschema.generator.TypeContext.getTypeConsideringHierarchyMatching(TypeContext.java:388) ~[jsonschema-generator-5.0.0.jar:na]
at com.github.victools.jsonschema.generator.TypeContext.getTypeWithAnnotation(TypeContext.java:367) ~[jsonschema-generator-5.0.0.jar:na]



# Feature 19_Moderation.

panels.jsx:538
GET http://localhost:8080/api/moderation?message=Erklaere%20kurz%2C%20was%20Content-Moderation%20ist. 500 (Internal Server Error)
(anonym)	@	panels.jsx:538
(anonym)