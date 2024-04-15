package app.simple.felicity.models.normal;

public class Folder {
    
    private String name;
    private String path;
    
    public Folder(String name, String path) {
        this.name = name;
        this.path = path;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
}
