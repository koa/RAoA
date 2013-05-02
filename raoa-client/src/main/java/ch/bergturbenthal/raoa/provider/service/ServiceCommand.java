package ch.bergturbenthal.raoa.provider.service;

import android.os.Parcel;
import android.os.Parcelable;

public enum ServiceCommand implements Parcelable {
	POLL, SCREEN_OFF, SCREEN_ON, START, STOP;

	public static final Creator<ServiceCommand> CREATOR = new Creator<ServiceCommand>() {
		@Override
		public ServiceCommand createFromParcel(final Parcel source) {
			return ServiceCommand.values()[source.readInt()];
		}

		@Override
		public ServiceCommand[] newArray(final int size) {
			return new ServiceCommand[size];
		}
	};

	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void writeToParcel(final Parcel dest, final int flags) {
		dest.writeInt(ordinal());
	}

}
