/*
 This file is part of the BlueJ program. 
 Copyright (C) 2012,2014,2016  Michael Kolling and John Rosenberg
 
 This program is free software; you can redistribute it and/or 
 modify it under the terms of the GNU General Public License 
 as published by the Free Software Foundation; either version 2 
 of the License, or (at your option) any later version. 
 
 This program is distributed in the hope that it will be useful, 
 but WITHOUT ANY WARRANTY; without even the implied warranty of 
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 GNU General Public License for more details. 
 
 You should have received a copy of the GNU General Public License 
 along with this program; if not, write to the Free Software 
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA. 
 
 This file is subject to the Classpath exception as provided in the  
 LICENSE.txt file that accompanied this code.
 */
package bluej.collect;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import bluej.compiler.CompileInputFile;
import bluej.compiler.CompileObserver;
import bluej.compiler.Diagnostic;
import bluej.compiler.EDTCompileObserver;
import bluej.pkgmgr.Project;

/**
 * A wrapper for a {@link CompileObserver} that also logs the
 * error messages to the data collector.
 * 
 * @author Neil Brown
 *
 */
public class DataCollectionCompileObserverWrapper implements EDTCompileObserver
{
    private EDTCompileObserver wrapped;
    private List<DiagnosticWithShown> diagnostics = new ArrayList<DiagnosticWithShown>();
    private CompileInputFile[] sources;
    private boolean automatic;
    private Project project;
    
    public DataCollectionCompileObserverWrapper(Project project, EDTCompileObserver wrapped)
    {
        this.project = project;
        this.wrapped = wrapped;
    }

    @Override
    public void startCompile(CompileInputFile[] sources, boolean automatic)
    {
        diagnostics.clear();
        this.sources = sources;
        this.automatic = automatic;
        wrapped.startCompile(sources, automatic);

    }

    @Override
    public boolean compilerMessage(Diagnostic diagnostic)
    {
        boolean shownToUser = wrapped.compilerMessage(diagnostic);
        File userFile = new File(diagnostic.getFileName());
        for (CompileInputFile input : sources)
        {
            if (input.getJavaCompileInputFile().getName().equals(userFile.getName()))
            {
                userFile = input.getUserSourceFile();
                break;
            }
        }
        diagnostics.add(new DiagnosticWithShown(diagnostic, shownToUser, userFile));
        return shownToUser;
    }

    @Override
    public void endCompile(CompileInputFile[] sources, boolean successful)
    {
        // Heuristic: if all files are in the same package, record the compile as being with that package
        // (I'm fairly sure the BlueJ interface doesn't let you do cross-package compile,
        // so I think this should always produce one package)
        Set<String> packages = new HashSet<String>();
        for (CompileInputFile f : sources)
        {
            packages.add(project.getPackageForFile(f.getJavaCompileInputFile()));
        }
        bluej.pkgmgr.Package pkg = packages.size() == 1 ? project.getPackage(packages.iterator().next()) : null;
        
        DataCollector.compiled(project, pkg, sources, diagnostics, successful, automatic);
        wrapped.endCompile(sources, successful);
    }

}
