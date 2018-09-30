/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package me.jamiemansfield.survey;

import me.jamiemansfield.bombe.analysis.CachingInheritanceProvider;
import me.jamiemansfield.bombe.analysis.InheritanceProvider;
import me.jamiemansfield.bombe.asm.analysis.ClassProviderInheritanceProvider;
import me.jamiemansfield.bombe.asm.jar.JarEntryRemappingTransformer;
import me.jamiemansfield.bombe.asm.jar.JarFileClassProvider;
import me.jamiemansfield.bombe.jar.Jars;
import me.jamiemansfield.lorenz.MappingSet;
import me.jamiemansfield.lorenz.io.MappingFormat;
import me.jamiemansfield.survey.remapper.SurveyRemapper;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/**
 * A fluent interface for using Survey's {@link SurveyRemapper}.
 *
 * @author Jamie Mansfield
 * @since 0.2.0
 */
public class SurveyMapper {

    private final MappingSet mappings;

    public SurveyMapper(final MappingSet mappings) {
        this.mappings = mappings;
    }

    public SurveyMapper() {
        this(MappingSet.create());
    }

    /**
     * Loads mappings from the given path, using the given reader.
     *
     * @param mappingsPath The path to the mappings file
     * @param format The mapping format to use for reading the mappings file
     * @return {@code this}, for chaining
     */
    public SurveyMapper loadMappings(final Path mappingsPath, final MappingFormat format) {
        try {
            format.read(this.mappings, mappingsPath);
        }
        catch (final IOException ignored) {
        }
        return this;
    }

    /**
     * Remaps the given input jar, with the loaded mappings, and saves it to
     * the given output path.
     *
     * @param input The input jar
     * @param output The output jar
     */
    public void remap(final Path input, final Path output) {
        try (final JarFile jarFile = new JarFile(input.toFile());
             final JarOutputStream jos = new JarOutputStream(Files.newOutputStream(output))) {
            final InheritanceProvider inheritance =
                    new CachingInheritanceProvider(new ClassProviderInheritanceProvider(new JarFileClassProvider(jarFile)));
            Jars.transform(jarFile, jos,
                    new JarEntryRemappingTransformer(
                            new SurveyRemapper(this.mappings, inheritance),
                            SurveyClassRemapper::new
                    )
            );
        }
        catch (final IOException ex) {
            ex.printStackTrace();
        }
    }

    // TODO: Use the new ASM7 stuff (that I introduced \o/)
    private static class SurveyClassRemapper extends ClassRemapper {

        public SurveyClassRemapper(final ClassVisitor classVisitor, final Remapper remapper) {
            super(classVisitor, remapper);
        }

        @Override
        public void visitInnerClass(final String name, final String outerName, final String innerName, final int access) {
            // We need to change the inner name as ASM doesn't
            final String mappedName = this.remapper.map(name);
            super.visitInnerClass(
                    name,
                    outerName,
                    // This check is for anonymous classes
                    innerName == null ? null : mappedName.substring(mappedName.lastIndexOf('$') + 1),
                    access
            );
        }

    }

}
