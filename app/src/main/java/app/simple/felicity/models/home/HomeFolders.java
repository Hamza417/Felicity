package app.simple.felicity.models.home;

import java.util.ArrayList;

import app.simple.felicity.models.normal.Folder;

public class HomeFolders extends Home {
    
    private ArrayList <Folder> folders;
    
    public HomeFolders(int title, int icon, ArrayList <Folder> folders) {
        super(title, icon, folders.size());
        this.folders = folders;
    }
    
    public ArrayList <Folder> getFolders() {
        return folders;
    }
    
    public void setFolders(ArrayList <Folder> folders) {
        this.folders = folders;
        setSize(folders.size());
    }
}
