package djxy.models.resource;

public class ImageResource extends Resource {

    private String name;

    public ImageResource(String url, String name) {
        super(url);
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public Resource clone() {
        return new ImageResource(getUrl(), getName());
    }
}