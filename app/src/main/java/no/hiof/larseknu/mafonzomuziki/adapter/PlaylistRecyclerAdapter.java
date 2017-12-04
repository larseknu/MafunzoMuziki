package no.hiof.larseknu.mafonzomuziki.adapter;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import java.util.List;

import kaaes.spotify.webapi.android.models.PlaylistSimple;
import no.hiof.larseknu.mafonzomuziki.MainActivity;
import no.hiof.larseknu.mafonzomuziki.R;

/**
 * Created by larseknu on 01/12/2017.
 */

public class PlaylistRecyclerAdapter extends RecyclerView.Adapter<PlaylistRecyclerAdapter.PlaylistViewHolder> {
    private List<PlaylistSimple> playlists;
    private LayoutInflater inflater;

    private View.OnClickListener clickListener;

    public PlaylistRecyclerAdapter (Context context, List<PlaylistSimple> playlists) {
        this.playlists = playlists;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public PlaylistViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.playlist_list_item, parent, false);
        PlaylistViewHolder holder = new PlaylistViewHolder(view);
        return holder;
    }

    @Override
    public void onBindViewHolder(PlaylistViewHolder holder, int position) {
        PlaylistSimple currentPlaylist = playlists.get(position);
        holder.setData(currentPlaylist);

        if (clickListener != null) {
            holder.itemView.setOnClickListener(clickListener);
        }
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    public void setOnItemClickListener(View.OnClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public class PlaylistViewHolder extends RecyclerView.ViewHolder {
        TextView playlistTitle;
        ImageView playlistCoverArt;

        public PlaylistViewHolder(View itemView) {
            super(itemView);
            playlistTitle = itemView.findViewById(R.id.playlist_title);
            playlistCoverArt = itemView.findViewById(R.id.playlist_cover_art);
        }

        public void setData(PlaylistSimple playlist) {
            this.playlistTitle.setText(playlist.name);

            if (!playlist.images.isEmpty()) {
                Picasso.with(playlistCoverArt.getContext())
                        .load(playlist.images.get(0).url)
                        .into(playlistCoverArt);
            }
        }
    }
}
