/*
 * Created on 05-Nov-2004 at 15:57:47.
 * 
 * Copyright (c) 2004-2005 Robert Virkus / Enough Software
 *
 * This file is part of J2ME Polish.
 *
 * J2ME Polish is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * J2ME Polish is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with J2ME Polish; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 * 
 * Commercial licenses are also available, please
 * refer to the accompanying LICENSE.txt or visit
 * http://www.j2mepolish.org for details.
 */
package de.enough.polish.dataeditor;

/**
 * <p>GUI implementations should implement and register this interface, so that the data-model can notify the GUI about events.</p>
 *
 * <p>Copyright Enough Software 2004 - 2011</p>

 * <pre>
 * history
 *        05-Nov-2004 - rob creation
 * </pre>
 * @author Robert Virkus, j2mepolish@enough.de
 */
public interface DataEditorUI {
	
	void signalChangedData();
	
	void signalChangedDefinition();
	
	void signalUnchangedData();
	
	void signalUnchangedDefinition();

}
