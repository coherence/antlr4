/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */

package org.antlr.v4.test.runtime;

import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.lang.reflect.Field;
import java.util.Set;

/**
 I think I figured out how to use annotation processors in maven.  It's
 more or less automatic and you don't even need to tell maven, with one minor
 exception. The idea is to create a project for the annotation and another
 for the annotation processor. Then, a project that uses the annotation
 can simply set up the dependency on the other projects. You have to turn
 off processing, -proc:none on the processor project itself but other than
 that, java 6+ more or less tries to apply any processors it finds during
 compilation. maven just works.

 Also you need a META-INF/services/javax.annotation.processing.Processor file
 with "org.antlr.v4.test.runtime.CommentHasStringValueProcessor" in it.
 */
@SupportedAnnotationTypes({"org.antlr.v4.test.runtime.CommentHasStringValue"})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class CommentHasStringValueProcessor extends AbstractProcessor {

	protected JavacElements utilities;
	protected TreeMaker treeMaker;

	@Override
	public synchronized void init(ProcessingEnvironment processingEnv) {
		super.init(processingEnv);
		utilities = (JavacElements)processingEnv.getElementUtils();
		treeMaker = TreeMaker.instance(extractContext(utilities));
	}

	private static Context extractContext(JavacElements utilities) {
		try {
			Field compilerField = JavacElements.class.getDeclaredField("javaCompiler");
			compilerField.setAccessible(true);
			JavaCompiler compiler = (JavaCompiler)compilerField.get(utilities);
			Field contextField = JavaCompiler.class.getDeclaredField("context");
			contextField.setAccessible(true);
			return (Context)contextField.get(compiler);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
//		Messager messager = processingEnv.getMessager(); // access compilation output
		Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(CommentHasStringValue.class);
		for (Element annotatedElement : annotatedElements) {
//			messager.printMessage(Diagnostic.Kind.NOTE, "element:"+annotatedElement.toString());
			String docComment = utilities.getDocComment(annotatedElement);
			JCTree.JCLiteral literal = treeMaker.Literal(docComment!=null ? docComment : "");
			JCTree elementTree = utilities.getTree(annotatedElement);
			if ( elementTree instanceof JCTree.JCVariableDecl ) {
				((JCTree.JCVariableDecl)elementTree).init = literal;
			}
			else if ( elementTree instanceof JCTree.JCMethodDecl ) {
				JCTree.JCStatement[] statements = new JCTree.JCStatement[1];
				statements[0] = treeMaker.Return(literal);
				((JCTree.JCMethodDecl)elementTree).body = treeMaker.Block(0, List.from(statements));
			}
		}
		return true;
	}

	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latestSupported();
	}
}
