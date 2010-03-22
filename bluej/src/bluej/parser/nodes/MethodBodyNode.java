/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2009  Michael Kolling and John Rosenberg 
 
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
package bluej.parser.nodes;

import bluej.parser.EditorParser;
import bluej.parser.lexer.JavaTokenTypes;
import bluej.parser.lexer.LocatableToken;
import bluej.parser.nodes.NodeTree.NodeAndPosition;

/**
 * A node representing a method or constructor body.
 * 
 * @author Davin McCall
 */
public class MethodBodyNode extends IncrementalParsingNode
{
    public MethodBodyNode(ParsedNode parent)
    {
        super(parent);
        setInner(true);
    }
    
    protected boolean isDelimitingNode(NodeAndPosition nap)
    {
        return nap.getNode().isContainer();
    }
    
    protected LocatableToken doPartialParse(EditorParser parser)
    {
        return parser.parseStatement(parser.getTokenStream().nextToken());
    }
    
    protected boolean lastPartialCompleted(EditorParser parser, LocatableToken token)
    {
        return token != null;
    }
    
    protected boolean isNodeEndMarker(int tokenType)
    {
        return tokenType == JavaTokenTypes.RCURLY;
    }
}
