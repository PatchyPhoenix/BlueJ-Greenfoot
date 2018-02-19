/*
 This file is part of the Greenfoot program.
 Copyright (C) 2005-2009,2010,2014,2015,2016,2017,2018  Poul Henriksen and Michael Kolling

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
package greenfoot.guifx.images;

import bluej.Config;
import bluej.pkgmgr.Project;
import bluej.pkgmgr.target.ClassTarget;
import bluej.utility.Debug;
import bluej.utility.DialogManager;
import bluej.utility.FileUtility;
import bluej.utility.javafx.FXConsumer;
import bluej.utility.javafx.JavaFXUtil;
import greenfoot.guifx.PastedImageNameDialog;
import greenfoot.guifx.images.ImageLibList.ImageListEntry;
import greenfoot.util.ExternalAppLauncher;
import greenfoot.util.GreenfootUtil;

import java.io.File;
import java.io.IOException;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * A Pane for selecting a class image. The image can be selected from either the
 * project image library, or the greenfoot library, or an external location.
 *
 * @author Davin McCall
 * @author Amjad Altadmri
 */
class ImageLibPane extends VBox
{
    private final Project project;
    private final Window container;

    private ImageLibList projImageList;
    private ImageLibList greenfootImageList;

    private File projImagesDir;
    private ObjectProperty<File> selectedImageFile = new SimpleObjectProperty<>(null);

    /** Menu items that are in the context menu. */
    private MenuItem editItem;
    private MenuItem duplicateItem;
    private MenuItem deleteItem;

    /** Suffix used when creating a copy of an existing image (duplicate) */
    private static final String COPY_SUFFIX = Config.getString("imagelib.duplicate.image.name.suffix");
    /** PopupMenu icon */
    private static final String DROPDOWN_ICON_FILE = "menu-button.png";
    /** A watcher that goes notified when an image is selected, to allow for previewing. May be null */
    private ImageSelectionWatcher selectionWatcher;

    /**
     * Construct ImageLibPane with a known classTarget and selectionWatcher.
     * Usually used by the SelectImageFrame for selecting an image for an existing class.
     *
     * @param container         The contained frame
     * @param classTarget       The class target of the existing class
     * @param selectionWatcher  The image selection watcher
     */
    ImageLibPane(Window container, ClassTarget classTarget, ImageSelectionWatcher selectionWatcher)
    {
        this(container, classTarget.getPackage().getProject(), getSpecifiedImage(classTarget), selectionWatcher);
    }

    /**
     * Construct ImageLibPane. Usually used by the NewImageClassFrame for creating an new image class.
     *
     * @param container   The contained window
     * @param project     The current project
     */
    ImageLibPane(Window container, Project project)
    {
        this(container, project, null, null);
    }

    /**
     * A private construct for ImageLibPane to build assign the fields and build the controls.
     *
     * @param container         The contained window
     * @param project           The current project
     * @param specifiedImage    The image to be selected initially
     * @param selectionWatcher  The image selection watcher
     */
    private ImageLibPane(Window container, Project project, File specifiedImage, ImageSelectionWatcher watcher)
    {
        super(10);
        this.container = container;
        this.project = project;
        this.selectionWatcher = watcher;

        getChildren().addAll(buildImageLists(specifiedImage), buildBottomBar());
    }

    /**
     * Build Image selection panels - project and greenfoot image library.
     *
     * @param specifiedImage The image to be selected. Could be null.
     * @return a Pane containing the image lists.
     */
    private Pane buildImageLists(File specifiedImage)
    {
        GridPane listsGridPane = new GridPane();
        listsGridPane.setVgap(5);
        ColumnConstraints constraints = new ColumnConstraints();
        constraints.setHgrow(Priority.SOMETIMES);
        listsGridPane.getColumnConstraints().setAll(constraints, constraints, constraints);

        // Project images panel
        projImagesDir = new File(project.getProjectDir(), "images");
        projImageList = new ImageLibList(projImagesDir, true);
        projImageList.select(specifiedImage);
        JavaFXUtil.runRegular(Duration.millis(1000), () -> projImageList.refresh());
        ScrollPane imageScrollPane = new ScrollPane(projImageList);
        imageScrollPane.setFitToWidth(true);
        imageScrollPane.setFitToHeight(true);
        GridPane.setVgrow(imageScrollPane, Priority.ALWAYS);
        GridPane.setMargin(imageScrollPane, new Insets(0, 10, 0, 0));
        listsGridPane.addColumn(0, new Label(Config.getString("imagelib.projectImages")), imageScrollPane);

        // List of greenfoot images
        greenfootImageList = new ImageLibList(false);

        JavaFXUtil.addChangeListener(projImageList.getSelectionModel().selectedItemProperty(), imageListEntry -> valueChanged(imageListEntry, true));
        JavaFXUtil.addChangeListener(greenfootImageList.getSelectionModel().selectedItemProperty(), imageListEntry -> valueChanged(imageListEntry, false));

        // Category selection panel
        ImageCategorySelector imageCategorySelector = new ImageCategorySelector(new File(Config.getGreenfootLibDir(), "imagelib"));
        imageCategorySelector.setImageLibList(greenfootImageList);

        ScrollPane categoryScrollPane = new ScrollPane(imageCategorySelector);
        categoryScrollPane.setFitToWidth(true);
        categoryScrollPane.setFitToHeight(true);
        listsGridPane.addColumn(1, new Label(Config.getString("imagelib.categories")), categoryScrollPane);

        ScrollPane greenfootImagesScrollPane = new ScrollPane(greenfootImageList);
        greenfootImagesScrollPane.setFitToWidth(true);
        greenfootImagesScrollPane.setFitToHeight(true);
        listsGridPane.addColumn(2, new Label(Config.getString("imagelib.images")), greenfootImagesScrollPane);

        VBox.setVgrow(listsGridPane, Priority.ALWAYS);
        return listsGridPane;
    }

    /**
     * Builds the bar at the bottom which contains the cog menu and the help label.
     * Cog menu provides option for adding new images and editing current ones.
     * Help label prompts the user to select an image from one of the lists.
     *
     * @return The bottom bar as a Pane
     */
    private Pane buildBottomBar()
    {
        Label helpLabel = JavaFXUtil.withStyleClass(new Label(Config.getString("imagelib.help.selectImage")), "dialog-error-label");
        helpLabel.visibleProperty().bind(selectedImageFile.isNull());
        return new BorderPane(null, null, helpLabel, null, createCogMenu());
    }

    /**
     * Creates the cog button, which contains options for a selected image and options to
     * add new or imported images to the project.
     *
     * @return a Menu Button representing the cog.
     */
    private MenuButton createCogMenu()
    {
        editItem = createSelectedEntryMenuItem("imagelib.edit", "imagelib.edit.tooltip", this::editImage);
        duplicateItem = createSelectedEntryMenuItem("imagelib.duplicate", "imagelib.duplicate.tooltip", this::duplicateSelected);
        deleteItem = createSelectedEntryMenuItem("imagelib.delete", "imagelib.delete.tooltip", this::confirmDelete);

        return new MenuButton(null,
                new ImageView(new Image(ImageLibPane.class.getClassLoader().getResourceAsStream(DROPDOWN_ICON_FILE))),
                editItem, duplicateItem, deleteItem, new SeparatorMenuItem(),
                createGeneralMenuItem("imagelib.create.button", "imagelib.create.tooltip", event -> createNewImage()),
                createGeneralMenuItem("imagelib.paste.image", "imagelib.paste.tooltip", event -> pasteImage()),
                createGeneralMenuItem("imagelib.import.button", "imagelib.import.tooltip", event -> importImage()));
    }

    /**
     * Create a MenuItem for an image list entry, assign an action to it and disable it initially.
     *
     * @param label     The label of the menu item.
     * @param tooltip   The text of the tooltip to show.
     * @param consumer  The action to be performed on the selected entry.
     * @return A menu item which invokes the action passed on the selected image list entry.
     */
    private MenuItem createSelectedEntryMenuItem(String label, String tooltip, FXConsumer<ImageListEntry> consumer)
    {
        MenuItem item = new MenuItem(Config.getString(label));
        Tooltip.install(item.getGraphic(), new Tooltip(Config.getString(tooltip)));
        item.setDisable(true);
        item.setOnAction(event -> {
            ImageListEntry entry = projImageList.getSelectionModel().getSelectedItem();
            if (entry != null && entry.getImageFile() != null)
            {
                consumer.accept(entry);
            }
        });
        return item;
    }

    /**
     * Create a general MenuItem and assign an action to it.
     *
     * @param label         The label of the menu item.
     * @param tooltip       The text of the tooltip to show.
     * @param eventHandler  The action to be performed by the menu item.
     * @return A menu item which invokes the passed action.
     */
    private MenuItem createGeneralMenuItem(String label, String tooltip, EventHandler<ActionEvent> eventHandler)
    {
        MenuItem item = new MenuItem(Config.getString(label));
        Tooltip.install(item.getGraphic(), new Tooltip(Config.getString(tooltip)));
        item.setOnAction(eventHandler);
        return item;
    }

    /**
     * Create a new image through new image dialog.
     */
    private void createNewImage()
    {
        new NewImageDialog(container, projImagesDir).showAndWait().ifPresent(file -> {
            projImageList.refresh();
            projImageList.select(file);
            selectImage(file);
        });
    }

    /**
     * An image was selected/unselected in one of the ImageLibLists
     *
     * @param entry            The image entry selected/unselected
     * @param isProjImageList  True if the entry effected is in the
     *                         project images' list, false otherwise.
     */
    private void valueChanged(ImageListEntry entry, boolean isProjImageList)
    {
        if (entry != null && entry.getImageFile() != null)
        {
            if(isProjImageList)
            {
                greenfootImageList.getSelectionModel().clearSelection();
            }
            else
            {
                projImageList.getSelectionModel().clearSelection();
            }
            selectImage(entry.getImageFile());
            setItemButtons(isProjImageList);
        }
        else
        {
            // handle the no-image image entry.
            // This is for un-selecting an entry, e.g. by clear selection.
            selectImage(null);
            setItemButtons(false);
        }
    }

    /**
     * Change the three selection based menu items to the
     * parameter provided.
     * @param state To enable or disable the menu item buttons.
     */
    private void setItemButtons(boolean state)
    {
        editItem.setDisable(!state);
        duplicateItem.setDisable(!state);
        deleteItem.setDisable(!state);
    }

    /**
     * Selects the given file (or no file) for use in the preview.
     * 
     * @param imageFile  The file to select, and to show in the small preview box in the
     *                   SelectImageFrame. If null, then "no image" is selected.
     */
    private void selectImage(File imageFile)
    {
        selectedImageFile.set(imageFile);
        if (selectionWatcher != null)
        {
            selectionWatcher.imageSelected(imageFile);
        }
    }

    /**
     * Gets specified image file (which will be project images/ directory) for this specific
     * class, without searching super classes (see getClassImage for that).  Returns null if none
     * specified.
     */
    private static File getSpecifiedImage(ClassTarget gclass)
    {
        // TODO test this
        String imageName = gclass.getProperty("image");
        
        // If an image is specified for this class, and we can read it, return
        if (imageName != null && !imageName.equals(""))
        {
            return new File(new File("images"), imageName).getAbsoluteFile();
        }
        
        return null;
    }

    private void importImage()
    {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Config.getString("imagelib.browse.button"));
        // TODO make it ImageView instead?
        // new ImageFilePreview(chooser);

        chooser.getExtensionFilters().addAll(
                new ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif"),
                new ExtensionFilter("All Files", "*.*"));

        File selectedFile = chooser.showOpenDialog(container);
        if (selectedFile != null)
        {
            File newFile = new File(projImagesDir, selectedFile.getName());
            GreenfootUtil.copyFile(selectedFile, newFile);
            if(projImageList != null)
            {
                projImageList.select(newFile);
            }
        }
    }

    /**
     * Get the selected image file
     */
    public ObjectProperty<File> selectedImageProperty()
    {
        return selectedImageFile;
    }

    /**
     * Create a new file which is an exact copy of the parameter
     * image, and select it if it has been craeted successfully.
     *
     * @param entry Cannot be null, nor can its imageFile.
     */
    private void duplicateSelected(ImageListEntry entry)
    {
        File srcFile = entry.getImageFile();
        File dstFile = null;
        File dir = srcFile.getParentFile();
        String fileName = srcFile.getName();
        int index = fileName.lastIndexOf('.');
        
        String baseName;
        String ext;
        if (index != -1)
        {
            baseName = fileName.substring(0, index);
            ext = fileName.substring(index + 1);
        } 
        else
        {
            baseName = fileName;
            ext = "";
        }
        baseName += ("_" + COPY_SUFFIX);
        
        try
        {
            dstFile = GreenfootUtil.createNumberedFile(dir, baseName, ext);
            FileUtility.copyFile(srcFile, dstFile);
        }
        catch (IOException e)
        {
            Debug.reportError(e);
        }

        if (dstFile != null)
        {
            projImageList.select(dstFile);
        }
    }
    
    /**
     * Confirms whether or not to delete the selected file.
     * @param entry Cannot be null, nor can its imageFile.
     */
    private void confirmDelete(ImageListEntry entry)
    {
        //TODO change this
        String text = Config.getString("imagelib.delete.confirm.text") + " " + entry.getImageFile().getName() + "?";
        ButtonType optionResult = new Alert(Alert.AlertType.CONFIRMATION, text, ButtonType.YES, ButtonType.NO).showAndWait().orElse(ButtonType.NO);
        if (optionResult == ButtonType.YES)
        {
            entry.getImageFile().delete();
            projImageList.refresh();
        }
    }

    /**
     * Opens an external app to edit an image file.
     *
     * @param entry The list entry contains the image.
     */
    private void editImage(ImageListEntry entry)
    {
        ExternalAppLauncher.editImage(entry.getImageFile());
    }

    private void pasteImage()
    {
        if (Clipboard.getSystemClipboard().hasImage())
        {
            Image image = Clipboard.getSystemClipboard().getImage();
            new PastedImageNameDialog(container, image, projImagesDir).showAndWait().ifPresent(file -> {
                projImageList.refresh();
                projImageList.select(file);
                selectImage(file);
            });
        }
        else
        {
            DialogManager.showErrorFX(container, "no-clipboard-image-data");
        }
    }
}
